# Sprint 2 — ghi chú cho frontend (S2-10)

Backend Sprint 2 đã xong. Tài liệu này tóm tắt phần **frontend cần đổi**; chi tiết
request/response xem [API-ENDPOINTS.md](../API-ENDPOINTS.md) và `openapi.yaml`.

## 1. Breaking change: trạng thái booking mới

- Booking mới **không còn** `in Bearbeitung`; backend trả **`bestätigt`**.
- Bổ sung trạng thái: `zahlung ausstehend`, `eingecheckt`, `nicht erschienen`, `abgelaufen`.
- Terminal (không sửa được): `abgeschlossen`, `storniert`, `nicht erschienen`, `abgelaufen`.
- Việc cần làm:
  - Cập nhật type/enum + nhãn i18n cho đủ 8 trạng thái.
  - Bỏ giả định "booking mới = in Bearbeitung".
  - Disable nút huỷ/đổi lịch khi status terminal.

## 2. Field mới trên booking

```json
{
  "confirmationCode": "TT-7K3M9Q",
  "versionNo": 0,
  "cancellation": {
    "late": true, "feeAmount": 0, "refundAmount": 0,
    "reason": "Kundenwunsch", "requestedByRole": "USER",
    "cancelledAt": "2026-11-19T10:15:30"
  }
}
```

- `confirmationCode`: hiển thị cho khách (mã tra cứu).
- `versionNo`: tăng sau mỗi lần đổi lịch.
- `cancellation`: chỉ có sau khi huỷ; `late = true` nghĩa là huỷ trong 24h.

## 3. Endpoint mới

| Endpoint | Dùng cho |
|---|---|
| `PATCH /bookings/:id/reschedule` | đổi lịch (cutoff 24h) |
| `PATCH /bookings/:id/no-show` | admin đánh dấu khách không đến |
| `GET /bookings/:id/history` | timeline trạng thái |
| `POST /bookings/lookup` + `/lookup/verify` | khách tra cứu bằng code + SĐT + OTP |
| `POST /waitlist`, `GET /waitlist`, `DELETE /waitlist/:id?phone=` | waitlist |
| `GET /audit` | audit log (ADMIN) |

## 4. Huỷ lịch (D4)

- Trong 24h **vẫn cho huỷ** (mặc định), response 200 + `cancellation.late = true`.
  Không còn lỗi `CANCEL_TOO_LATE` cho luồng khách (trừ khi admin bật `BLOCK`).
- Lỗi mới cần map: `LATE_CANCEL_BLOCKED` (409), `BOOKING_TERMINAL` (409).
- Hiển thị cảnh báo "Stornierung innerhalb von 24 Stunden" thay vì chặn.

## 5. Idempotency

- Gửi `Idempotency-Key: <uuid>` khi POST `/bookings` (sinh 1 lần cho mỗi lần submit).
- Retry do timeout/mạng → dùng **cùng key** để không tạo booking trùng.
- Map lỗi: `IDEMPOTENCY_KEY_REUSED` (409 — key dùng cho payload khác),
  `IDEMPOTENCY_IN_PROGRESS` (409 — chờ và retry sau).

## 6. No-show

- Khách vượt 3 lần no-show/180 ngày → `409 NO_SHOW_BLOCKED` khi đặt lịch.
- Hiển thị thông báo + hotline; không tự gửi `overrideNoShow` từ client khách.
- Admin có thể đánh dấu no-show trước giờ bằng `{ "override": true, "reason": "..." }`.

## 7. Phân trang admin

- `GET /bookings` / `GET /customers`: **không** `page` → array cũ; **có** `page` →
  `PageResponse { content, page, size, totalElements, totalPages, first, last }`.
- Filter booking: `status`, `staffId`, `serviceId`, `from`, `to`, `q`, `sort`.
- `CustomerDTO` thêm `noShowCount`.

## 8. Mã lỗi mới

`RESCHEDULE_TOO_LATE`, `REASON_REQUIRED`, `INVALID_CONFIRMATION_CODE`, `INVALID_OTP`,
`OTP_EXPIRED`, `OTP_TOO_MANY_ATTEMPTS`, `LATE_CANCEL_BLOCKED`, `WAITLIST_DUPLICATE`,
`NO_SHOW_BLOCKED`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_IN_PROGRESS`,
`CHECK_IN_TOO_EARLY`, `NO_SHOW_TOO_EARLY`, `BOOKING_TERMINAL`, `TOO_MANY_REQUESTS`.

## 9. Dev/test

- OTP chỉ trả trong response khi backend chạy `GUEST_LOOKUP_EXPOSE_OTP=true`
  (dev/test). Production phải `false` và tích hợp SMS.
- Container dev: `docker compose up -d --build` (Flyway tự chạy V1→V3).
