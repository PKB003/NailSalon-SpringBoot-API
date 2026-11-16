# fiskaly SIGN DE — đăng ký sandbox & runbook tích hợp

> Quyết định D17: TSE = **fiskaly SIGN DE** (cloud TSE). Tài liệu này phục vụ S0-10
> (đăng ký sandbox, lưu secret, ký thử 1 giao dịch) và là đầu vào cho S3-T10 → S3-T12.
> Endpoint cụ thể có thể thay đổi theo version API — luôn đối chiếu
> [tài liệu fiskaly](https://developer.fiskaly.com) trước khi code.

## 0. Trạng thái hiện tại

- [ ] Có tài khoản fiskaly sandbox
- [ ] Có `FISKALY_API_KEY` / `FISKALY_API_SECRET`
- [ ] Có `FISKALY_TSS_ID` (TSS = Technical Security System)
- [ ] Có `FISKALY_CLIENT_ID` + `serial_number`
- [ ] Ký thử 1 giao dịch, lưu được signature + QR
- [ ] Có file DATEV mẫu + mẫu hoá đơn của Steuerberater

## 1. Đăng ký sandbox (S0-10)

1. Tạo tài khoản tại dashboard fiskaly, chọn môi trường **Sandbox/Test**.
2. Tạo **Organisation** (pháp nhân của tiệm). Ghi lại `organisation_id`.
3. Vào mục API keys, tạo key cho sandbox:
   - `FISKALY_API_KEY`
   - `FISKALY_API_SECRET` (chỉ hiện 1 lần — lưu ngay vào secret manager)
4. Xác nhận base URL sandbox: `https://kassensichv.io/api/v2` (kiểm tra lại trong dashboard;
   production dùng host khác).
5. Không tạo TSS/client bằng tay trên production khi chưa có sign-off của Steuerberater.

## 2. Tạo TSS và client (sandbox)

```bash
# 1) Lấy access token (hết hạn nhanh — cache trong RAM, không lưu DB)
curl -s -X POST "$FISKALY_BASE_URL/auth" \
  -H "Content-Type: application/json" \
  -d "{\"api_key\":\"$FISKALY_API_KEY\",\"api_secret\":\"$FISKALY_API_SECRET\"}"

# 2) Tạo/khởi tạo TSS (idempotent theo tss_id; state: CREATED -> INITIALIZED)
#    Lưu ý: sandbox có thể yêu cầu admin_puk theo tài liệu hiện hành.
curl -s -X PUT "$FISKALY_BASE_URL/tss/$FISKALY_TSS_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"state":"INITIALIZED","description":"TT Nails Sandbox"}'

# 3) Tạo client (mỗi máy/quầy 1 client; serial_number là chuỗi duy nhất)
curl -s -X PUT "$FISKALY_BASE_URL/tss/$FISKALY_TSS_ID/client/$FISKALY_CLIENT_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"serial_number":["TT-NAILS-SANDBOX-01"]}'
```

> Nếu tài liệu fiskaly của bạn dùng `POST /tse` thay cho `PUT /tss/{id}`, giữ nguyên
> nguyên tắc: **idempotent theo id**, lưu lại id vào secret manager.

## 3. Lưu secret

| Biến | Nơi lưu |
|---|---|
| `FISKALY_API_KEY`, `FISKALY_API_SECRET` | GitHub Environments secret / server secret manager |
| `FISKALY_TSS_ID`, `FISKALY_CLIENT_ID` | cùng nơi (không phải secret tuyệt mật nhưng không hardcode) |
| `FISKALY_BASE_URL` | config theo môi trường (sandbox/production) |
| `FISKALY_ENABLED` | `false` ở dev/test; `true` khi đã có credential |

- **Không** commit `.env` (đã gitignore) và không đưa key vào `application.properties`.
- Local: copy `.env.example` → `.env` rồi điền.
- CI: dùng GitHub Environment `staging` / `production` với required reviewers.

## 4. Ký thử một giao dịch

Luồng tối thiểu (S3 sẽ đóng gói trong `TseClient`):

```bash
TX_ID=$(uuidgen)

# 4.1 Bắt đầu giao dịch
curl -s -X PUT "$FISKALY_BASE_URL/tss/$FISKALY_TSS_ID/tx/$TX_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"state\": \"ACTIVE\",
    \"client_id\": \"$FISKALY_CLIENT_ID\",
    \"schema\": {
      \"standard_v1\": {
        \"receipt\": {
          \"receipt_type\": \"RECEIPT\",
          \"amounts_per_vat_rate\": { \"7\": [\"0.00\", \"0.00\"] },
          \"amounts_per_payment_type\": { \"CASH\": [\"0.00\", \"0.00\"] }
        }
      }
    }
  }"

# 4.2 Kết thúc giao dịch -> nhận signature + QR payload
curl -s -X PATCH "$FISKALY_BASE_URL/tss/$FISKALY_TSS_ID/tx/$TX_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"state":"FINISHED"}'
```

Kỳ vọng: response chứa `signature.value`, `signature.counter`, `signature.algorithm`,
`qr_code_data` (tên field theo version API). Lưu nguyên payload vào bảng `tse_transaction`.

**Đánh dấu hoàn thành S0-10 khi:** giao dịch thử trả signature hợp lệ và QR đọc được;
secret đã nằm trong secret manager; có file DATEV mẫu từ Steuerberater.

## 5. Thiết kế tích hợp trong code (S3)

```
com.ttnails.booking_service.tse
├── TseClient                 (interface: start, finish, status)
├── FiskalyTseClient          (implementation REST)
├── TseTransaction            (entity append-only)
└── TseReconciliationJob      (đối soát cuối ngày)
```

Cấu hình dự kiến (đã có placeholder trong `application.properties`):

```properties
fiskaly.enabled=${FISKALY_ENABLED:false}
fiskaly.base-url=${FISKALY_BASE_URL:https://kassensichv.io/api/v2}
fiskaly.api-key=${FISKALY_API_KEY:}
fiskaly.api-secret=${FISKALY_API_SECRET:}
fiskaly.tss-id=${FISKALY_TSS_ID:}
fiskaly.client-id=${FISKALY_CLIENT_ID:}
```

Luồng checkout:

```
POS checkout
  → tạo payment + invoice trong 1 transaction DB
  → tseClient.start(txId)                 // txId = UUID sinh 1 lần
  → tseClient.finish(txId, receiptJson)   // nhận signature + qr
  → lưu tse_transaction (append-only)
  → in QR + TSE thông tin lên hoá đơn
  → timeout/mất mạng: giữ TSE_PENDING, retry CÙNG txId, không chặn thu tiền
  → job cuối ngày: mọi giao dịch phải có signature trước Z-report
```

Quy tắc bắt buộc:

- `tx_id` **không đổi khi retry** (idempotency của fiskaly).
- Không xoá `tse_transaction` — là bằng chứng pháp lý (GoBD).
- `fiskaly.enabled=false` → checkout vẫn chạy, chỉ bỏ bước ký (dùng cho dev/test).
- Không log `api_secret`, access token, hay toàn bộ receipt JSON ở mức INFO.

## 6. DSFinV-K & DATEV

- **DSFinV-K**: export từ `invoice` + `payment` + `tse_transaction`, **không tính lại** từ giá hiện tại.
  Test S3-T11: mở file bằng tool của Steuerberater, không lỗi cấu trúc.
- **DATEV**: CSV theo SKR03/04; `tax_code`/`revenue_account` lấy từ `tax_config` snapshot
  tại thời điểm phát hành hoá đơn. Test S3-T13 với file mẫu thật.
- **§19 UStG**: hoá đơn ghi “Keine Umsatzsteuer gemäß §19 UStG”; VAT = 0 nhưng vẫn lưu
  `tax_mode=SMALL_BUSINESS` để sau này chuyển `STANDARD_19` mà không sửa hoá đơn cũ.

## 7. Checklist S3 liên quan TSE

- [ ] `S3-T10` fiskaly sandbox + `tse_transaction` + QR trên receipt
- [ ] `S3-T11` DSFinV-K export
- [ ] `S3-T12` Test concurrency cấp số + retry TSE + mất mạng
- [ ] `S3-T13` Steuerberater review hoá đơn mẫu + file DATEV
- [ ] `S3-T14` Gửi Mitteilung §146a Abs. 4 AO trước go-live

## 8. Ba câu hỏi cần Steuerberater trả lời bằng văn bản

1. **§19 & ngưỡng:** tiệm còn trong ngưỡng Kleinunternehmer không, ngày nào phải chuyển,
   có được giữ §19 khi mở tiệm thứ 2 cùng pháp nhân không.
2. **Nghĩa vụ TSE & Belegausgabe:** có bắt buộc TSE với mô hình hiện tại không; có xin
   Belegausgabeverzicht được không; mẫu thông báo Finanzamt.
3. **Hạch toán tip/commission:** 20% tiệm giữ lại ghi vào tài khoản nào; tip qua thẻ
   hạch toán thế nào; tax key/revenue account DATEV cho từng loại dịch vụ.
