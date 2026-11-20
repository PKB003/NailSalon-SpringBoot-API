# Thiết kế tích hợp TSE — fiskaly SIGN DE (S3)

> **Trạng thái:** 🟨 Khung code + tài liệu hoàn tất ngày **2026-11-20**.
> **Chưa đăng ký key fiskaly** → chưa gọi API thật, `FISKALY_ENABLED=false`.
> **Liên quan:** D17 (Plan §5.8.3), S3-09, S3-T10 → S3-T14.
>
> Tài liệu này là nguồn sự thật cho việc triển khai cụ thể sau khi có credential.
> Khi có key, **cập nhật mục 12 (activation runbook)** và đánh dấu checklist.

---

## 0. TL;DR

| Hạng mục | Hiện tại (2026-11-20) |
|---|---|
| Provider | fiskaly SIGN DE (cloud TSE) — D17 |
| Credential | ❌ chưa đăng ký (không có API key/TSS/client) |
| Code | ✅ `TseClient` + `NoopTseClient` + `TseProperties` + `GET /tse/status` |
| Ký giao dịch thật | ❌ `FiskalyTseClient` chỉ là fail-fast guard (S3-T10) |
| Bảng `tse_transaction` | 📝 thiết kế xong, **chưa tạo migration** |
| DSFinV-K | 📝 thiết kế xong, chưa export |
| Ảnh hưởng checkout | Không. `enabled=false` → mọi luồng chạy bình thường, ký bị bỏ qua |

**Nguyên tắc bất biến:** TSE **không được chặn thu tiền**. Nếu fiskaly lỗi/timeout,
payment + invoice vẫn commit; giao dịch TSE ở trạng thái `PENDING` và được retry
với **cùng `tx_id`** trước Z-report cuối ngày.

---

## 1. Phạm vi

### Trong phạm vi (S3-T10 → T12)
- Ký mỗi giao dịch tiền (payment/invoice) bằng TSE.
- Lưu append-only `tse_transaction` (bằng chứng pháp lý, GoBD).
- Retry idempotent theo `tx_id`; job đối soát cuối ngày.
- QR TSE in trên receipt.
- Export DSFinV-K từ dữ liệu đã chốt.

### Ngoài phạm vi
- Payment/invoice/tax engine (S3-01 → S3-08) — TSE chỉ tiêu thụ kết quả.
- POS UI (S3-10) — frontend hiển thị QR, backend chỉ trả payload.
- Đăng ký pháp lý với Finanzamt (§146a Abs. 4 AO) — việc của PO, không phải code.
- Hardware TSE — chỉ giữ interface để đổi provider.

### Điều kiện tiên quyết
1. Hoàn tất invoice + payment (S3-01 → S3-06) để có `invoice_id`/amounts ổn định.
2. Có credential sandbox fiskaly (S0-10 — **đang thiếu**).
3. Steuerberater xác nhận nghĩa vụ TSE + mẫu receipt/Belegausgabe (Plan §5.8.5).

---

## 2. Kiến trúc code hiện tại

```
com.ttnails.booking_service.tse
├── client
│   ├── TseClient              (interface: start/finish/status)
│   ├── NoopTseClient          (@ConditionalOnProperty enabled=false)
│   ├── FiskalyTseClient       (guard fail-fast; S3-T10 sẽ thay bằng REST client)
│   ├── TseReceipt / TseVatAmount
│   ├── TseSignature
│   ├── TseReceiptType / TsePaymentType
│   ├── TseTransactionStatus
│   └── TseProviderStatus
├── config
│   └── TseProperties          (fiskaly.*, mask(), hasCredentials())
├── controller
│   └── TseController          (GET /tse/status — MANAGER/ADMIN)
├── dto
│   └── TseStatusResponse
└── service
    └── TseStatusService
```

**Vì sao tách interface:** Plan §5.6.3 yêu cầu giữ `TseClient` để đổi sang hardware
TSE/USB nếu tiệm mất mạng thường xuyên. Mọi logic checkout chỉ phụ thuộc interface,
không phụ thuộc fiskaly.

**Vì sao `FiskalyTseClient` fail-fast:** tránh production bật `FISKALY_ENABLED=true`
nhưng thực tế không ký — sai luật mà không ai biết. Khi triển khai xong, thay
constructor bằng `RestClient` + kiểm tra credential.

---

## 3. Luồng checkout + ký TSE

### 3.1 Luồng thành công

```
POS checkout
  │
  ├─ (1) DB transaction: payment(s) + invoice + invoice lines + tip allocation
  │        └─ sinh txId = UUID (một lần, lưu vào tse_transaction trước khi gọi mạng)
  │
  ├─ (2) TseClient.start(txId, receipt)      // PUT tx ACTIVE + receipt schema
  ├─ (3) TseClient.finish(txId)              // PATCH FINISHED → signature + QR
  ├─ (4) UPDATE tse_transaction status=SIGNED, signature, qr_payload, finished_at
  │
  ├─ (5) Trả receipt DTO cho POS (có QR payload)
  └─ (6) COMMIT
```

Quy ước transaction:
- Bước (1) và (4) nằm trong **cùng transaction DB** nếu fiskaly trả lời trong timeout.
- Nếu (2)/(3) timeout → vẫn commit payment/invoice + `tse_transaction.status=PENDING`.
- Không giữ DB transaction mở trong lúc chờ HTTP fiskaly quá `timeout-ms` (3s).
  Khuyến nghị: commit payment/invoice trước, sau đó ký + update trong transaction ngắn.
  Hệ quả: có thể có payment chưa có signature trong vài giây — chấp nhận được và
  được job đối soát xử lý.

### 3.2 Luồng lỗi

| Tình huống | Hành vi | Trạng thái |
|---|---|---|
| fiskaly timeout / mất mạng | Không rollback payment; lưu lỗi | `PENDING` |
| HTTP 5xx | Retry backoff; hết lượt → | `PENDING` (job retry tiếp) |
| HTTP 401 (token hết hạn) | Refresh token 1 lần rồi retry cùng `tx_id` | `PENDING` nếu vẫn lỗi |
| HTTP 409 (tx đã tồn tại) | `GET /tx/{tx_id}` để lấy trạng thái; không tạo tx mới | theo response |
| HTTP 4xx do payload sai | Không retry tự động; alert + log | `FAILED` |
| `enabled=false` | Bỏ ký, receipt không QR TSE | `SKIPPED` |

> **Không bao giờ** sinh `tx_id` mới cho cùng một payment. `tx_id` là idempotency key
> của fiskaly; đổi id = tạo giao dịch TSE thứ hai → sai sổ.

### 3.3 Storno

- Storno tạo một giao dịch TSE **mới** (receipt_type `CANCELLATION`), tham chiếu
  hoá đơn gốc; **không sửa/xoá** giao dịch gốc.
- `tse_transaction` có `invoice_id` + `storno_of_invoice_id` để DSFinV-K nối đúng.

---

## 4. Data model — `tse_transaction` (draft)

Chưa tạo migration ở bước này. Khi làm S3-T10, tạo `V4__sprint3_tse.sql`.

```sql
CREATE TABLE tse_transaction (
    id                  CHAR(36)      NOT NULL PRIMARY KEY,
    legal_entity_id     CHAR(36)      NULL,          -- multi-tenant readiness
    branch_id           CHAR(36)      NULL,
    invoice_id          CHAR(36)      NULL,
    payment_id          CHAR(36)      NULL,
    storno_of_invoice_id CHAR(36)     NULL,

    tx_id               CHAR(36)      NOT NULL,      -- idempotency key gửi fiskaly
    tx_number           VARCHAR(64)   NULL,          -- TSE cấp khi FINISHED
    tss_id              VARCHAR(64)   NULL,
    client_id           VARCHAR(64)   NULL,
    serial_number       VARCHAR(64)   NULL,

    receipt_type        VARCHAR(32)   NOT NULL,      -- RECEIPT | CANCELLATION
    receipt_json        JSON          NULL,          -- snapshot gửi TSE (append-only)
    signature_algorithm VARCHAR(64)   NULL,
    signature_value     VARCHAR(2048) NULL,
    signature_counter   BIGINT        NULL,
    qr_payload          TEXT          NULL,

    status              VARCHAR(16)   NOT NULL,      -- PENDING | SIGNED | FAILED | SKIPPED
    attempt_count       INT           NOT NULL DEFAULT 0,
    next_retry_at       DATETIME(6)   NULL,
    error_code          VARCHAR(64)   NULL,
    error_message       VARCHAR(512)  NULL,

    started_at          DATETIME(6)   NULL,
    finished_at         DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,

    CONSTRAINT uk_tse_tx_id UNIQUE (tx_id)
);
CREATE INDEX idx_tse_status_retry ON tse_transaction (status, next_retry_at);
CREATE INDEX idx_tse_invoice      ON tse_transaction (invoice_id);
CREATE INDEX idx_tse_created      ON tse_transaction (created_at);
```

Quy tắc:
- **Append-only:** không `DELETE`; sửa chỉ để cập nhật trạng thái/signature/retry.
  Nếu cần sửa nội dung pháp lý → tạo bản ghi mới + audit.
- `receipt_json` chứa PII? Chỉ chứa số tiền/loại giao dịch; **không** tên/SĐT khách.
- Lưu tối thiểu **10 năm** (GoBD). Backup + export định kỳ.
- `tx_id` unique toàn hệ thống, kể cả khi retry/khởi động lại.

---

## 5. State machine

```
                 enabled=false
create ─────────────────────────────► SKIPPED (terminal)
   │
   │ enabled=true
   ▼
PENDING ──(finish OK)───────────────► SIGNED (terminal)
   │  ▲
   │  └──(retry job, cùng tx_id)
   │
   └──(4xx payload / hết lượt retry)─► FAILED ──(admin retry có lý do)──► PENDING
```

| Từ | Sang | Điều kiện |
|---|---|---|
| — | `PENDING` | checkout tạo payment, bắt đầu gọi TSE |
| `PENDING` | `SIGNED` | có signature + QR hợp lệ |
| `PENDING` | `PENDING` | timeout/5xx, `attempt_count < max` |
| `PENDING` | `FAILED` | 4xx không retry được hoặc vượt max attempts |
| `FAILED` | `PENDING` | admin retry thủ công + audit lý do |
| — | `SKIPPED` | TSE tắt |

`SIGNED` và `SKIPPED` là terminal. `SKIPPED` **không** được xuất DSFinV-K như giao
dịch đã ký; export phải có cột cảnh báo để Steuerberater biết giai đoạn chưa bật TSE.

---

## 6. Idempotency, retry, timeout

### 6.1 Sinh `tx_id`
- Sinh **một lần** khi tạo payment, trước khi gọi mạng: `UUID.randomUUID()`.
- Lưu DB ngay (unique). Nếu request checkout retry ở tầng API, dùng lại `tx_id` cũ
  thay vì tạo mới — nên truyền `Idempotency-Key` của checkout xuống (xem S2-07).

### 6.2 Retry
- Chỉ retry lỗi **tạm thời**: timeout, connection reset, 5xx, 429.
- Không retry: 400/401 sau khi refresh token, 403, payload invalid.
- Backoff: `initial-backoff-ms × 2^attempt`, tối đa 3–5 lần trong request;
  sau đó để job đối soát retry với backoff dài hơn (5–15 phút).
- **Luôn dùng cùng `tx_id`**. Trước khi tạo mới, gọi `GET /tx/{tx_id}` để biết
  giao dịch đã `FINISHED` chưa.

### 6.3 Timeout
- HTTP connect/read timeout = `fiskaly.timeout-ms` (mặc định 3000ms).
- Retry tối đa `fiskaly.retry.max-attempts` (mặc định 3).
- Tổng thời gian chờ trong checkout không vượt ~5s; quá thì trả receipt tạm
  `TSE_PENDING` + hướng dẫn in lại sau.

### 6.4 Job đối soát cuối ngày
```
Mỗi 10 phút + trước Z-report:
  SELECT * FROM tse_transaction
   WHERE status IN ('PENDING','FAILED')
     AND (next_retry_at IS NULL OR next_retry_at <= now())
   ORDER BY created_at
   LIMIT 100;
  → GET /tx/{tx_id} (nguồn sự thật)
  → nếu FINISHED: lưu signature, status=SIGNED
  → nếu ACTIVE:    finish lại cùng tx_id
  → nếu CANCELLED/không tồn tại: tạo mới cùng tx_id (idempotent)
  → cập nhật attempt_count, next_retry_at, error_*
```
- Alert nếu còn `PENDING/FAILED` quá 24h hoặc trước khi đóng Z-report.
- Không chặn việc đóng ca nếu chỉ còn lỗi TSE, nhưng **phải hiển thị cảnh báo**
  cho quản lý và ghi vào Z-report.

---

## 7. Mapping receipt → payload fiskaly

> ⚠️ Tên field/format phụ thuộc **version API** của fiskaly. Trước khi code
> `FiskalyTseClient`, chốt version + lưu fixture thật vào `src/test/resources/fiskaly/`.

### 7.1 Receipt
| Nguồn | Field fiskaly (dự kiến) | Ghi chú |
|---|---|---|
| invoice/payment | `receipt_type` | `RECEIPT`; Storno → `CANCELLATION` (xác nhận lại) |
| VAT engine | `amounts_per_vat_rate` | Key = `"0"` (§19) hoặc `"19"`; value = cặp gross/net |
| payment split | `amounts_per_payment_type` | `CASH` / `NON_CASH`; CARD + BANK_TRANSFER → `NON_CASH` |
| tiền tệ | `currency` | luôn `EUR` |
| tip | **không đưa vào VAT base** | D15; có thể ghi nhận riêng nếu fiskaly hỗ trợ |

### 7.2 §19 UStG
- `tax_mode=SMALL_BUSINESS` → VAT rate = 0, `vat_amount = 0`.
- Receipt in **“Keine Umsatzsteuer gemäß §19 UStG”**.
- Khi chuyển `STANDARD_19`: dùng snapshot `tax_mode`/`tax_rate` của invoice,
  **không tính lại** hoá đơn cũ.

### 7.3 Tip & discount
- Discount/voucher làm giảm gross trước khi map sang `amounts_per_vat_rate`.
- Tip tách khỏi invoice service lines; nếu fiskaly yêu cầu đưa vào tổng giao dịch
  thì map thành dòng riêng **không VAT**, và Steuerberater phải xác nhận.
- Loyalty redeem = giảm giá (giảm VAT base), không phải payment.

### 7.4 Payment split
- Tổng `amounts_per_payment_type` phải = tổng `amounts_per_vat_rate` (gross),
  sai lệch → `400` ở checkout, không gọi TSE.
- Refund/Storno: giao dịch âm hoặc `CANCELLATION` theo xác nhận của fiskaly.

---

## 8. Cấu hình & secret

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `FISKALY_ENABLED` | `false` | **Không bật** trước khi S3-T10 xong |
| `FISKALY_ENVIRONMENT` | `SANDBOX` | `SANDBOX` \| `PRODUCTION` |
| `FISKALY_BASE_URL` | `https://kassensichv.io/api/v2` | Kiểm tra lại theo dashboard |
| `FISKALY_API_KEY` | — | Secret manager |
| `FISKALY_API_SECRET` | — | Secret manager, chỉ hiện 1 lần |
| `FISKALY_TSS_ID` | — | TSS = Technical Security System |
| `FISKALY_CLIENT_ID` | — | 1 client/quầy |
| `FISKALY_TIMEOUT_MS` | `3000` | Connect/read timeout |
| `FISKALY_RETRY_MAX_ATTEMPTS` | `3` | Trong request |
| `FISKALY_RETRY_BACKOFF_MS` | `500` | Backoff khởi điểm |

Quy tắc bảo mật:
- Không commit `.env`, không log `api_secret`/access token/receipt JSON đầy đủ.
- Access token cache trong RAM, **không** lưu DB; refresh trước khi hết hạn.
- `GET /tse/status` chỉ trả id đã mask + `credentialsPresent`, không trả key.
- Production dùng secret manager + required reviewer (GitHub Environment).

---

## 9. API surface

### Đã có
`GET /tse/status` — MANAGER/ADMIN.

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "enabled": false,
    "provider": "fiskaly",
    "environment": "SANDBOX",
    "implementation": "NOOP",
    "credentialsPresent": false,
    "tssId": "",
    "clientId": "",
    "timeoutMs": 3000
  }
}
```

### Dự kiến (chưa làm, chỉ khi có nhu cầu vận hành)
- `POST /tse/transactions/{txId}/retry` — ADMIN, bắt buộc `reason`, audit.
- `GET /tse/transactions?status=PENDING&page=` — ADMIN/MANAGER, phân trang.
- `GET /tse/reconciliation?date=` — tổng PENDING/FAILED/SIGNED trước Z-report.

Không expose endpoint public nào liên quan TSE.

---

## 10. QR trên receipt (Belegausgabe)

- QR chứa `qr_payload` do fiskaly trả; **không tự ghép** từ signature.
- In cùng: TSS serial, transaction number, signature counter, thời điểm ký.
- Receipt tối thiểu (theo §14 UStG + KassenSichV):
  - tên/địa chỉ tiệm, Steuernummer/USt-IdNr;
  - số hoá đơn, ngày phát hành, ngày dịch vụ;
  - mô tả dịch vụ, net/VAT/gross (hoặc ghi chú §19);
  - phương thức thanh toán, tip (nếu có);
  - QR TSE + “Keine Umsatzsteuer gemäß §19 UStG” khi áp dụng.
- Nếu in lại receipt: dùng đúng dữ liệu đã lưu, không ký lại/không sinh QR mới.

---

## 11. DSFinV-K export (S3-T11)

Nguyên tắc: **export từ dữ liệu đã chốt**, không tính lại từ giá/config hiện tại.

Nguồn dữ liệu:
| Nguồn | Dùng cho |
|---|---|
| `invoice` + `invoice_line` | doanh thu, VAT, số hoá đơn |
| `payment` | cash/non-cash, split, refund |
| `tse_transaction` | signature, tx number, TSS serial, QR |
| `cash_drawer_session` | Z-report/đóng ca |
| `tax_config` (snapshot trên invoice) | tax key, revenue account |

Kế hoạch:
1. Chốt version DSFinV-K với Steuerberater (hiện hành thường là 2.x).
2. Viết exporter đọc DB → thư mục file CSV/XML theo spec.
3. Validate bằng tool của Steuerberater; lưu file gốc + checksum.
4. Chỉ export kỳ đã đóng sổ; kỳ đang mở có thể export nháp.
5. Giao dịch `PENDING/FAILED` phải được xử lý trước export chính thức;
   nếu không, file phải đánh dấu rõ để không nộp sai.

> Chi tiết cấu trúc file sẽ bổ sung khi có bộ mẫu DSFinV-K từ Steuerberater.

---

## 12. Activation runbook — khi có key fiskaly

> Làm tuần tự, mỗi bước có bằng chứng. **Không** bật production trước bước 10.

1. **Đăng ký sandbox** (S0-10): organisation + API key/secret; lưu secret manager.
2. **Chốt version API**: ghi lại URL tài liệu + version; tải OpenAPI/schema nếu có.
3. **Tạo TSS** (`PUT /tss/{id}` hoặc `POST /tse` theo version) → lưu `FISKALY_TSS_ID`.
4. **Tạo client** (`PUT /tss/{id}/client/{id}`) → lưu `FISKALY_CLIENT_ID` + `serial_number`.
5. **Ký thử bằng curl** theo `docs/tse/fiskaly-setup.md` §4; lưu response mẫu.
6. **Viết fixture** `src/test/resources/fiskaly/*.json` từ response thật (auth/tx finished/error).
7. **Triển khai `FiskalyTseClient`** với `RestClient`: auth cache, start, finish, get tx.
8. **WireMock test**: happy path, 401 refresh, 409 đã tồn tại, timeout, 5xx, QR parse.
9. **Tạo migration V4** `tse_transaction` + repository + service orchestration.
10. **Sandbox E2E**: checkout thật trên staging, xác nhận `status=SIGNED` + QR đọc được;
    retry cùng `tx_id` không tạo giao dịch thứ hai.
11. **Test mất mạng**: tắt network giữa chừng → payment vẫn commit, `PENDING`,
    job đối soát ký lại thành `SIGNED`.
12. **Bật production** sau sign-off Steuerberater: đổi `FISKALY_ENVIRONMENT=PRODUCTION`,
    base-url production, key production; chạy 1 giao dịch thật có kiểm soát.
13. **Gửi Mitteilung §146a Abs. 4 AO** trong 1 tháng sau khi đưa vào sử dụng.
14. **Bật alert** PENDING > 24h, tỷ lệ FAILED > 0, token refresh fail.

**Điều kiện hoàn thành S3-T10:** bước 1–11 pass trên staging; có ảnh receipt QR;
có log/audit chứng minh retry cùng `tx_id`.

---

## 13. Testing strategy

| Tầng | Công cụ | Nội dung |
|---|---|---|
| Unit | JUnit | map receipt (§19/19%), state machine, mask, backoff |
| Client | WireMock/MockRestServiceServer | auth, start/finish, 401 refresh, 409, timeout, 5xx |
| Integration | H2/Testcontainers | payment + invoice + tse_transaction cùng transaction; rollback rules |
| Chaos | WireMock delay/error | timeout không chặn checkout; job retry thành công |
| Contract | fixture thật | parse đúng response version đang dùng |
| Concurrency | Testcontainers | 2 checkout cùng lúc → 2 tx_id khác nhau, không trùng |

CI **không cần key**: mặc định `FISKALY_ENABLED=false`; test client dùng WireMock.
Không đưa credential vào GitHub Actions cho tới khi có staging environment.

---

## 14. Observability

- Log có `txId`, `invoiceId`, `attempt`, `status`; **không** log secret/signature đầy đủ.
- Metrics đề xuất:
  - `tse_sign_total{status}` (SIGNED/PENDING/FAILED/SKIPPED)
  - `tse_sign_duration_seconds`
  - `tse_pending_age_seconds` (max)
  - `tse_auth_failures_total`
- Alert:
  - PENDING > 24h
  - FAILED > 0
  - token refresh fail > 3 lần liên tiếp
  - trước Z-report còn PENDING (cảnh báo mềm cho quản lý)

---

## 15. Bảo mật & tuân thủ

- `tse_transaction` là bằng chứng pháp lý: append-only, không xoá, lưu 10 năm.
- Audit log khi admin retry/override giao dịch `FAILED`.
- Không đưa PII khách vào `receipt_json` nếu không cần.
- Access token trong RAM; API secret chỉ trong secret manager.
- Backup DB + file DSFinV-K định kỳ; kiểm tra restore 6 tháng/lần.

---

## 16. Error taxonomy đề xuất

| `error_code` | Ý nghĩa | Retry? |
|---|---|---|
| `TSE_DISABLED` | `enabled=false` | Không (SKIPPED) |
| `TSE_TIMEOUT` | quá `timeout-ms` | Có |
| `TSE_NETWORK` | connection reset/DNS | Có |
| `TSE_AUTH_FAILED` | 401 sau refresh | Không (alert) |
| `TSE_TX_CONFLICT` | 409, cần GET tx | Có (GET trước) |
| `TSE_INVALID_RECEIPT` | 400 payload | Không (FAILED) |
| `TSE_PROVIDER_ERROR` | 5xx | Có |
| `TSE_NOT_CONFIGURED` | thiếu credential khi enabled | Không (fail-fast) |

---

## 17. Câu hỏi mở (chặn triển khai cụ thể)

1. **Version API fiskaly** đang dùng cho SIGN DE: endpoints, tên field `qr_code_data`
   hay `qr_payload`, format `amounts_per_*`.
2. **receipt_type** cho Storno: `CANCELLATION` hay giao dịch âm?
3. **Tip**: fiskaly có yêu cầu đưa tip vào receipt tổng hay tách riêng?
4. **DSFinV-K version** Steuerberater yêu cầu + tool validate.
5. **Nghĩa vụ TSE** với mô hình §19 + tiền mặt (Plan §5.8.5) — cần văn bản.
6. **Belegausgabe**: có xin `Belegausgabeverzicht` được không.

---

## 18. Task board liên quan TSE

| Task | Nội dung | Trạng thái |
|---|---|---|
| S3-T10a | Interface + config + no-op + `GET /tse/status` + tài liệu | ✅ 2026-11-20 |
| S3-T10b | Đăng ký sandbox + TSS/client + curl thử | ⬜ chờ key |
| S3-T10c | `FiskalyTseClient` (RestClient) + WireMock tests | ⬜ chờ T10b |
| S3-T10d | Migration `tse_transaction` + service orchestration | ⬜ chờ T10c |
| S3-T10e | QR trên receipt + sandbox E2E + retry cùng `tx_id` | ⬜ chờ T10d |
| S3-T11 | DSFinV-K export | ⬜ chờ T10e + mẫu Steuerberater |
| S3-T12 | Test concurrency cấp số + retry TSE + mất mạng | ⬜ một phần (TSE no-op đã có) |
| S3-T13 | Steuerberater review hoá đơn + DATEV | ⬜ |
| S3-T14 | Mitteilung §146a Abs. 4 AO | ⬜ trước go-live |

**Definition of Done cho TSE (S3-09):**
- [ ] Sandbox ký được, `tse_transaction.status=SIGNED`, QR đọc được.
- [ ] Retry cùng `tx_id` không tạo giao dịch thứ hai.
- [ ] Timeout/mất mạng không rollback payment/invoice.
- [ ] Job đối soát xử lý hết PENDING trước Z-report.
- [ ] DSFinV-K export validate được bằng tool của Steuerberater.
- [ ] Không có secret trong log/repo/response.
- [ ] Steuerberater sign-off + đã gửi Mitteilung (nếu áp dụng).
