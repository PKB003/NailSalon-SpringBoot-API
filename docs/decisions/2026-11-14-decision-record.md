# Biên bản chốt quyết định — 2026-11-14 (D1–D18)

> Bản sao lưu trữ của mục 15.1 trong [Plan.md](../../Plan.md). Mọi thay đổi phải tạo version mới
> kèm ngày hiệu lực; **không sửa hoá đơn/booking cũ**.

- **Người chốt:** Chủ tiệm / Product Owner
- **Phạm vi:** D1–D18 (quyết định nền tảng của dự án)
- **Ngày hiệu lực:** 2026-11-14

## 1. Danh sách quyết định

| ID | Chủ đề | Quyết định |
|---|---|---|
| D1 | Thị trường & ngôn ngữ | Đức — EUR — UI/hoá đơn tiếng Đức; API trả `errorCode` để FE map i18n |
| D2 | Chi nhánh | 1 chi nhánh MVP; schema có `branch_id` + `timezone` từ đầu |
| D3 | Nhân viên | Entity `staff` riêng (optional link `user`); role `NAIL_TECH`, `RECEPTIONIST`, `MANAGER` |
| D4 | Cọc / huỷ | MVP **không cọc**; huỷ miễn phí khi còn ≥ 24h; trong 24h `WARN` + ghi nhận late-cancel, **không chặn**; no-show chỉ đánh dấu |
| D5 | Giờ mở cửa & buffer | T2–T7 09:00–18:00, buffer 10 phút, Chủ nhật nghỉ; cấu hình theo branch/weekday |
| D6 | Thanh toán | MVP: tiền mặt + thẻ tại quầy + chuyển khoản; thanh toán sau khi xong dịch vụ; Stripe Phase 3 |
| D7 | Tích điểm | **Phương án 3**: điểm cố định 0,05 EUR + tier Silver/Gold/Platinum, hệ số 1,0/1,25/1,5, cap 10/15/20% |
| D8 | Tip & commission | Tip chia 80% thợ / 20% tiệm; commission dịch vụ theo cấu hình |
| D9 | Multi-service | Tuần tự, tối đa 3 dịch vụ, ưu tiên cùng 1 thợ, fallback nhiều thợ có handoff; auto-assign khi chọn "any" |
| D10 | Notification | Email + SMS cho MVP; Zalo OA Phase 3 |
| D11 | Thuế / kế toán | Ưu tiên "dễ khai thuế": hoá đơn §14 UStG, GoBD, DATEV export, TSE |
| D12 | Loyalty & hạng khách | Triển khai tier ngay trong MVP (Phương án 3) |
| D13 | Kênh thanh toán mở rộng | Chuyển khoản ghi nhận thủ công trong MVP; Stripe (Checkout/Terminal) Phase 3; thiết kế `PaymentGateway` adapter từ S3 |
| D14 | Hoá đơn & lưu trữ | PDF + số tuần tự không trống; lưu 10 năm; không sửa hoá đơn đã phát hành (chỉ Storno) |
| D15 | Tip & thuế | **Tip không chịu VAT**; tách khỏi doanh thu dịch vụ; không tính điểm; 80/20 chỉ là phân chia nội bộ |
| D16 | Kleinunternehmer §19 UStG | Hiện tại áp dụng §19; thiết kế **switchable** sang `STANDARD_19` bằng `tax_config`; hoá đơn cũ bất biến |
| D17 | TSE / POS provider | **fiskaly SIGN DE** (cloud TSE, REST, có sandbox); giữ interface `TseClient` để đổi provider |
| D18 | Ngưỡng hạng loyalty | Silver 0–299 / Gold 300–799 / Platinum ≥ 800 EUR trong 12 tháng; hệ số 1,0/1,25/1,5; cap 10/15/20% |

## 2. Hệ quả kỹ thuật bắt buộc

- Mọi bảng nghiệp vụ có `branch_id`; tiền dùng `BigDecimal` + `currency` từ S3 (baseline hiện tại còn `double`).
- Hoá đơn lưu `net`, `vat_rate`, `vat_amount`, `gross`, `tax_mode` tại thời điểm phát hành; **không tính lại** từ giá hiện tại.
- Tip là dòng riêng, không vào VAT base, không sinh loyalty point.
- `tse_transaction` append-only: mọi giao dịch tiền có `tx_id`, signature, QR; không xoá kể cả khi lỗi.
- Thời gian lưu UTC (`Instant`/`OffsetDateTime`) + `timezone` của branch; FE hiển thị local.
- Không hard-delete dữ liệu giao dịch; dùng `active`/`deleted_at` cho catalog.

## 3. Điều kiện thay đổi

Chỉ mở lại quyết định khi:

1. Có thay đổi pháp lý (UStG, GoBD, DSFinV-K, Finanzamt).
2. Steuerberater yêu cầu bằng văn bản.
3. Chủ tiệm đổi mô hình kinh doanh (multi-branch, cọc, Stripe…).

Khi thay đổi: tạo version mới của Plan kèm ngày hiệu lực; **không sửa hoá đơn/booking cũ**.

## 4. Còn chờ trước go-live

- [ ] Trả lời bằng văn bản của Steuerberater cho 3 câu hỏi ở mục 5.8.5 của Plan (tax mode, tip, DATEV).
- [ ] Đăng ký fiskaly sandbox và ký thử 1 giao dịch (xem [docs/tse/fiskaly-setup.md](../tse/fiskaly-setup.md)).
- [ ] Chữ ký sign-off của các vai trò (PO, Backend, Frontend, QA, DevOps, Steuerberater).

## 5. Sign-off

| Vai trò | Tên | Xác nhận | Ngày |
|---|---|---|---|
| Product Owner / Chủ tiệm | | [ ] | |
| Backend Lead | | [ ] | |
| Frontend Lead | | [ ] | |
| QA Lead | | [ ] | |
| DevOps | | [ ] | |
| Steuerberater | | [ ] | |
