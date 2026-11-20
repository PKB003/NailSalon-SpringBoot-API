# Sprint 3 — POS, Payment, Invoice & Tax (đang làm)

> Bắt đầu **2026-11-20**. Nhánh làm việc: `master` (commit theo từng task).
> Mục tiêu theo Plan.md §6: POS + payment/split, hoá đơn §14 UStG, tax engine, tip,
> Z-report, DATEV, **TSE/fiskaly**, DSFinV-K.

## Trạng thái tổng quan

| Task | Nội dung | Trạng thái |
|---|---|---|
| S3-01 | Entity payment/split/refund/invoice/tax/tip + migration | ⬜ chưa bắt đầu |
| S3-02 | API checkout (dịch vụ thực tế, add-on, discount, tip, split) | ⬜ chưa bắt đầu |
| S3-03 | Deposit config + `PaymentGateway` cho Stripe | ⬜ chưa bắt đầu |
| S3-04 | Tax engine §19 / VAT 19% + snapshot | ⬜ chưa bắt đầu |
| S3-05 | Invoice numbering (row lock) + PDF §14 + Storno | ⬜ chưa bắt đầu |
| S3-06 | Tip 80/20 + payout + commission | ⬜ chưa bắt đầu |
| S3-07 | Z-report + Kassenbuch + đối soát quỹ | ⬜ chưa bắt đầu |
| S3-08 | DATEV/CSV + báo cáo VAT tháng | ⬜ chưa bắt đầu |
| S3-09 | Tích hợp fiskaly SIGN DE | 🟨 khung + tài liệu xong, chờ key (T10a ✅) |
| S3-10 | POS UI + in receipt QR | ⬜ frontend ngoài repo |
| S3-11 | DSFinV-K export + thông báo Finanzamt | 📝 thiết kế xong |
| S3-12 | Test idempotency/split/refund/concurrency/TSE timeout | 🟨 TSE no-op đã có test |

## Đợt 1 (2026-11-20) — TSE/fiskaly groundwork, chưa có key

Yêu cầu: *code phần fiskaly trước, tài liệu hoá, triển khai cụ thể sau, chưa đăng ký key.*

### Đã làm
- `tse` module:
  - `TseClient` (start/finish/status) + `TseReceipt`, `TseSignature`,
    `TseTransactionStatus`, `TseProviderStatus`, `TseReceiptType`, `TsePaymentType`.
  - `NoopTseClient` — mặc định khi `fiskaly.enabled=false`; trả `SKIPPED`, không gọi mạng.
  - `FiskalyTseClient` — guard fail-fast, chưa implement REST (S3-T10).
  - `TseProperties` — bind `fiskaly.*`, `hasCredentials()`, `mask()`.
  - `GET /tse/status` (MANAGER/ADMIN) — không lộ secret.
- Cấu hình: `application.properties`, `.env.example` thêm environment/timeout/retry.
- Tài liệu:
  - [`docs/tse/tse-design.md`](../tse/tse-design.md) — thiết kế đầy đủ.
  - [`docs/tse/fiskaly-setup.md`](../tse/fiskaly-setup.md) — cập nhật trạng thái + runbook.
  - [`API-ENDPOINTS.md`](../../API-ENDPOINTS.md) — endpoint `/tse/status`.
- Test: `TseStatusTest` (6) — RBAC, status mặc định, no-op không ném lỗi, mask.

### Chưa làm (có lý do)
- Không gọi API fiskaly: **chưa có key**.
- Không tạo bảng `tse_transaction`: chờ chốt payload/version API + invoice/payment (S3-01/05),
  tránh migration phải sửa.
- Không bật `FISKALY_ENABLED=true`: `FiskalyTseClient` fail-fast.

## Thứ tự triển khai tiếp theo

1. **S3-01/S3-04/S3-05** — payment/invoice/tax engine + invoice numbering. TSE cần
   `invoice_id`, amounts theo VAT rate và payment split ổn định.
2. **Đăng ký fiskaly sandbox** (S0-10) → lưu secret → chốt version API → fixture.
3. **S3-T10c/d/e** — client REST + migration `tse_transaction` + orchestration + QR.
4. **S3-02/S3-06/S3-07** — checkout API, tip, Z-report.
5. **S3-08** — DATEV/VAT report; **S3-11** — DSFinV-K.
6. **S3-12** — test concurrency/retry/mất mạng; **S3-T13/T14** — sign-off + thông báo.

## Cấu hình TSE hiện tại

```properties
fiskaly.enabled=false          # KHÔNG bật trước S3-T10
fiskaly.environment=SANDBOX
fiskaly.base-url=https://kassensichv.io/api/v2
fiskaly.api-key=
fiskaly.api-secret=
fiskaly.tss-id=
fiskaly.client-id=
fiskaly.timeout-ms=3000
fiskaly.retry.max-attempts=3
fiskaly.retry.initial-backoff-ms=500
```

Kiểm tra nhanh:

```bash
# login admin rồi gọi status
curl -H "Authorization: Bearer <token>" http://localhost:8080/tse/status
# → implementation=NOOP, enabled=false, credentialsPresent=false
```

## Definition of Done Sprint 3 (rút gọn)

- [ ] 100% payment có invoice; tổng invoice = tổng payment.
- [ ] Số hoá đơn tuần tự, test 20 thread không trùng/không trống.
- [ ] §19 và VAT 19% đều đúng; tip không vào VAT base.
- [ ] Z-report khớp tiền mặt; DATEV mở được bằng Excel/DATEV.
- [ ] TSE sandbox: signature + QR, retry cùng `tx_id` không nhân đôi.
- [ ] `TSE_PENDING/FAILED` được xử lý trước Z-report.
- [ ] DSFinV-K validate bằng tool Steuerberater.
- [ ] Không có secret trong repo/log/response.

## Blocker

| Blocker | Ảnh hưởng | Cần ai |
|---|---|---|
| Chưa có fiskaly sandbox key | Không thể ký thử/chốt payload | PO/DevOps |
| Chưa có xác nhận Steuerberater về TSE/Belegausgabe | Có thể đổi phạm vi receipt | PO |
| Chưa có mẫu DATEV + DSFinV-K version | Chưa làm S3-08/S3-11 | Steuerberater |
| Invoice/payment chưa có | TSE chưa có nguồn dữ liệu để ký | Backend (S3-01→05) |
