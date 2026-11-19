# 📡 API Endpoints — Hướng dẫn cho Frontend

> **Backend đã hoàn thiện đầy đủ các endpoint trong API Contract.** Tài liệu này mô tả
> shape JSON **thực tế** mà backend trả về, kèm ví dụ request/response.

## Cấu hình chung

- **Base URL:** đặt `VITE_API_URL=http://localhost:8080` (backend chạy port 8080, KHÔNG có prefix `/api`).
  Nếu frontend tự nối thêm đường dẫn kiểu `${VITE_API_URL}/auth/login` thì dùng giá trị trên.
- **CORS:** đã mở sẵn cho `http://localhost:3000` và `http://localhost:5173`
  (có thể đổi qua property `app.cors.allowed-origins`).
- **Auth:** gửi header `Authorization: Bearer <token>` cho mọi endpoint (trừ đăng nhập/đăng ký,
  GET `/services`, GET `/availability`, GET `/bookings/availability`, POST `/bookings`,
  POST `/bookings/lookup`, POST `/bookings/lookup/verify`, POST `/waitlist` và
  DELETE `/waitlist/:id?phone=...` — các endpoint này public).
- **401** (token thiếu/hết hạn/logout) → frontend tự xóa token, về `/auth`.
- **403** (đã đăng nhập nhưng không đủ quyền — ví dụ gọi endpoint admin bằng token client).
- **Vai trò (S1):** `USER` (khách), `NAIL_TECH` (thợ — chỉ lịch của mình),
  `RECEPTIONIST` (lễ tân — đọc lịch, đổi trạng thái), `MANAGER` (quản lý thợ/ca),
  `ADMIN` (toàn quyền). `POST /users` giờ chỉ ADMIN (trước đây public).

### Envelope response

Mọi response **thành công** có dạng:

```json
{
  "code": 200,
  "message": "Success",
  "timestamp": "2026-11-16T10:30:00",
  "traceId": "8f14e45f-...",
  "data": { ... }
}
```

- `timestamp` (mới) và `timeStamp` (cũ) cùng tồn tại 1 sprint.
- `data` là payload; backend vẫn serialize thêm `result` (alias cũ) để không phá Postman/test cũ.
- `traceId` trùng với header `X-Request-Id` — dùng khi gửi log cho backend.

Mọi response **lỗi** có dạng:

```json
{
  "code": 409,
  "errorCode": "SLOT_FULL",
  "message": "This time slot is full. Please choose another time.",
  "details": [
    { "field": "date", "message": "Date must have format yyyy-MM-dd" }
  ],
  "traceId": "8f14e45f-...",
  "timestamp": "2026-11-16T10:30:00"
}
```

- HTTP status khớp `code`: 400/401/403/404/405/409/415/500.
- `errorCode` là mã ổn định để frontend map i18n (ví dụ `VALIDATION_FAILED`, `INVALID_DATE`,
  `SLOT_FULL`, `CANCEL_TOO_LATE`, `ACCESS_DENIED`).
- `details` chỉ có với lỗi validation.

### Phân trang (từ Sprint 0)

`GET /users?page=0&size=20&sortBy=name&direction=asc` trả:

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3,
  "first": true, "last": false }
```

`size` tối đa 100; `sortBy` chỉ nhận `name|email|phone|dob`.

### Tài liệu API

- Swagger UI: `GET /swagger-ui.html`
- OpenAPI JSON: `GET /v3/api-docs`
- Contract đóng băng v1: [`openapi.yaml`](openapi.yaml) (dùng để generate TS client).

### Tài khoản admin mặc định (seed tự động)

`emailOrPhone: admin@gmail.com` — mật khẩu lấy từ biến môi trường `ADMIN_PASSWORD`.
Nếu bỏ trống, backend sinh mật khẩu ngẫu nhiên và in **một lần** trong log khởi động
(tìm dòng `ADMIN_PASSWORD is not set`). Test dùng `12345678`.

---

## 1. Authentication

### POST `/auth/login` (public)
```json
// Request — 3 cách đều được
{ "emailOrPhone": "anna.m@email.de", "password": "..." }   // ← frontend đang dùng
{ "email": "anna.m@email.de", "password": "..." }
{ "phone": "017612345678", "password": "..." }
```
```json
// Response → data
{
  "token": "eyJhbGciOi...",
  "user": {
    "id": "3f2c...-uuid",
    "name": "Anna Müller",
    "email": "anna.m@email.de",
    "phone": "017612345678",
    "role": "USER",                // USER | NAIL_TECH | RECEPTIONIST | MANAGER | ADMIN
    "birthday": "1990-03-15"       // optional, null nếu không có
  }
}
```
> SĐT có/không khoảng trắng đều khớp (`"0176 12345678"` ≡ `"017612345678"`).

### POST `/auth/register` (public)
Request: `{ "name", "email", "phone", "password", "birthday": "1990-03-15"? }`
Response: giống login — `{ token, user }` với `user.role = "client"`.
Lỗi trùng email/phone: `409 { message: "Email already exists" | "Phone already exists" }`.

### GET `/auth/me`
Response → `data` = object `user` như trên.

### PUT `/auth/me`
Request: `{ "name"?, "email"?, "phone"?, "birthday"? }` (chỉ gửi field muốn đổi).
Response → `data` = `user` đã cập nhật.

### PUT `/auth/password`
Request: `{ "currentPassword", "newPassword" }`
Response → `data` = `{ "success": true }`. Sai mật khẩu hiện tại: `400 { message: "Current password is incorrect" }`.

## 2. Services

| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/services` | public | Danh sách dịch vụ |
| POST | `/services` | admin | Tạo mới |
| PUT | `/services/:id` | admin | Cập nhật |
| DELETE | `/services/:id` | admin | Xóa |

Shape (GET → `data` là mảng; POST/PUT → `data` là object):
```json
{
  "id": 1,
  "name": "Maniküre",
  "description": "Klassische Nagelpflege mit Lack in Wunschfarbe",
  "duration": 45,          // phút
  "price": 28.0,           // EUR
  "kategorie": "Nagelpflege",
  "beliebtheit": "hoch"
}
```
Body POST/PUT: `{ "name"*, "description", "duration"* (>=5), "price"* , "kategorie", "beliebtheit" }`.
Backend đã **seed sẵn 6 dịch vụ mẫu** khi DB trống.

---

## 3. Availability (Sprint 1)

### GET `/availability?date=YYYY-MM-DD&serviceIds=1,2&staffId=3` (public)

Engine availability v2: chỉ trả slot mà thợ **có ca**, **có đủ kỹ năng**, **không nghỉ phép**
và **không trùng booking** chưa terminal. Hỗ trợ đặt 1–3 dịch vụ liên tiếp bởi cùng 1 thợ.

```json
// Request
GET /availability?date=2026-11-20&serviceIds=2,1
// staffId optional — bỏ trống = mọi thợ đủ điều kiện
```
```json
// Response → data
{
  "date": "2026-11-20",
  "timezone": "Europe/Berlin",
  "slotStepMinutes": 30,
  "totalDurationMinutes": 105,       // Σ duration (60 + 45), KHÔNG gồm buffer
  "staff": [
    { "staffId": 1, "name": "Minh",
      "slots": [ { "start": "09:00", "end": "10:45" }, { "start": "09:30", "end": "11:15" } ] },
    { "staffId": 2, "name": "Linh", "slots": [] }
  ],
  "anyAvailable": ["09:00", "09:30", "10:00"]   // union giờ bắt đầu, đã sắp xếp
}
```

- `end = start + Σduration`. Buffer 10' **chỉ cộng ở cuối toàn bộ appointment**
  (`bufferEnd = start + Σ(duration + buffer)`), nên slot kế tiếp phải bắt đầu sau `bufferEnd`.
- `serviceIds` nhận `serviceIds=1,2` hoặc `serviceIds=1&serviceIds=2`; tối đa 3
  (quá 3 → `400 TOO_MANY_SERVICES`). `serviceId` đơn lẻ vẫn được chấp nhận.
- Lỗi: `400 INVALID_DATE` (sai định dạng), `400 INVALID_REQUEST` (thiếu dịch vụ),
  `404` (service/staff không tồn tại).
- **Đây là endpoint frontend nên dùng** (p95 < 300ms trên dữ liệu seed).

### GET `/bookings/availability?date=YYYY-MM-DD` (legacy — deprecated)
```json
// Response → data
{ "slots": ["09:00", "09:30", "10:00", ...] }   // union giờ bắt đầu của mọi thợ
```
Giữ 1 sprint để tương thích; dùng `GET /availability` thay thế.

---

## 4. Bookings

### POST `/bookings` (public — có token sẽ tự gắn user, không token vẫn đặt được)

S1: gửi `serviceIds` (1–3, ưu tiên) hoặc `serviceId` (legacy), và có thể chọn `staffId`.

```json
// Request — 2 dịch vụ liên tiếp với thợ cụ thể
{ "serviceIds": [2, 1], "staffId": 1,
  "date": "2026-11-20", "time": "09:00",
  "name": "Anna Müller", "phone": "0176 12345678",
  "email": "anna.m@email.de", "note": "Wünsche zum Design" }
```
```json
// Response → data
{
  "id": 101,
  "serviceId": 2,                     // dịch vụ đầu tiên
  "service": "Pediküre + Maniküre",
  "dienstleistung": "Pediküre + Maniküre",
  "date": "20.11.2026",               // dd.MM.yyyy
  "datum": "20.11.2026",
  "time": "09:00",                    // HH:mm
  "uhrzeit": "09:00",
  "endDate": "20.11.2026",            // start + Σduration (không gồm buffer)
  "endTime": "10:45",
  "endUhrzeit": "10:45",
  "name": "Anna Müller",
  "kunde": "Anna Müller",
  "phone": "0176 12345678",
  "email": "anna.m@email.de",
  "note": "Wünsche zum Design",
  "employee": "Minh",                 // tên thợ
  "mitarbeiter": "Minh",
  "staffId": 1,
  "staffName": "Minh",
  "status": "bestätigt",              // S2: booking mới KHÔNG còn "in Bearbeitung"
  "confirmationCode": "TT-7K3M9Q",    // S2: mã tra cứu cho guest
  "versionNo": 0,                     // S2: tăng mỗi lần reschedule
  "cancellation": null,               // S2: chỉ có sau khi huỷ
  "price": "63 €",                    // snapshot tại thời điểm đặt
  "priceSnapshot": 63.00,
  "currency": "EUR",
  "totalDurationMinutes": 105,
  "items": [
    { "serviceId": 2, "name": "Pediküre", "startTime": "09:00", "endTime": "10:00",
      "duration": 60, "price": 35.0, "currency": "EUR", "staffId": 1, "staffName": "Minh" },
    { "serviceId": 1, "name": "Maniküre", "startTime": "10:00", "endTime": "10:45",
      "duration": 45, "price": 28.0, "currency": "EUR", "staffId": 1, "staffName": "Minh" }
  ]
}
```

- Các dịch vụ chạy **liên tiếp không gap**; buffer dồn ở cuối (`items` không có buffer).
- `staffId` null → tự chọn thợ **ít phút bận nhất trong ngày** (tie-break `id` ASC).
- Đặt cùng slot cho cùng thợ một cách đồng thời: chỉ **1 request thành công**;
  request còn lại nhận `409 SLOT_TAKEN` (khóa staff row + kiểm tra overlap trong transaction).
- Không thợ nào đủ điều kiện (thiếu skill/không có ca/ngày nghỉ) → `409 NO_STAFF_AVAILABLE`.
- Ngoài giờ mở cửa hoặc phút lệch bước 30' → `400 OUTSIDE_OPENING_HOURS` / `400 INVALID_SLOT`.
- Quá 3 dịch vụ → `400 TOO_MANY_SERVICES`; service/thợ không tồn tại → `404`.
- **S2-07 Idempotency:** gửi header `Idempotency-Key: <uuid>`. Retry cùng key + cùng body
  trả lại **đúng booking cũ** (không tạo mới); cùng key nhưng body khác →
  `409 IDEMPOTENCY_KEY_REUSED`; key đang xử lý → `409 IDEMPOTENCY_IN_PROGRESS` (retry sau).
- **S2-05 no-show:** khách có ≥ 3 lần `nicht erschienen` trong 180 ngày →
  `409 NO_SHOW_BLOCKED`. Staff có thể gửi thêm `"overrideNoShow": true` để bỏ qua (audit).

### Booking shape chung
Backend trả **song song field tiếng Anh (client view) và tiếng Đức (admin view)** trong cùng
1 object nên cả 2 view dùng chung shape:
- `id`, `serviceId`, `phone`, `email`, `note`
- client view: `service`, `date`, `time`, `endDate`, `endTime`, `name`, `employee`, `status`, `price` (`"63 €"`)
- admin view: `dienstleistung`, `datum`, `uhrzeit`, `endUhrzeit`, `kunde`, `mitarbeiter` (giá trị trùng field tiếng Anh)
- S1: `staffId`, `staffName`, `priceSnapshot` (EUR), `currency`, `totalDurationMinutes`, `items[]`
- S2: `confirmationCode`, `versionNo`, `cancellation { late, feeAmount, refundAmount, reason, requestedByRole, cancelledAt }`
- `status`: `zahlung ausstehend` | `bestätigt` | `eingecheckt` | `in Bearbeitung` |
  `abgeschlossen` | `storniert` | `nicht erschienen` | `abgelaufen`.
  Booking mới luôn `bestätigt` (D4 — MVP không cọc).
  **Terminal (bất biến):** `abgeschlossen`, `storniert`, `nicht erschienen`, `abgelaufen`.

| Method | Endpoint | Quyền | Response |
|---|---|---|---|
| GET | `/bookings/my` | token khách | `booking[]` của khách, mới nhất trước |
| GET | `/bookings` | ADMIN/MANAGER/RECEPTIONIST | `booking[]` (không `page`) hoặc `PageResponse` (có `page`) — xem S2-09 |
| GET | `/bookings/:id/history` | chủ booking hoặc staff | `[{ id, fromStatus, toStatus, actorId, actorLabel, actorRole, reason, createdAt }]` |
| PATCH | `/bookings/:id/cancel` | chủ booking hoặc ADMIN/MANAGER/RECEPTIONIST | booking (`status = "storniert"`) + `cancellation` |
| PATCH | `/bookings/:id/reschedule` | chủ booking hoặc ADMIN/MANAGER/RECEPTIONIST | booking mới + `versionNo` |
| PATCH | `/bookings/:id/status` | ADMIN/MANAGER/RECEPTIONIST | booking — body `{ "status": "...", "employee"?: "...", "reason"?: "..." }` |
| PATCH | `/bookings/:id/no-show` | ADMIN/MANAGER/RECEPTIONIST | booking (`status = "nicht erschienen"`) |
| POST | `/bookings/lookup` | public | tạo OTP tra cứu (S2-08) |
| POST | `/bookings/lookup/verify` | public | booking sau khi xác thực OTP (S2-08) |

#### Huỷ lịch — chính sách 24h (D4)

- Khách huỷ khi còn **≥ 24h**: miễn phí, `cancellation.late = false`.
- Khách huỷ **trong 24h**: mặc định `WARN` → vẫn huỷ được, `cancellation.late = true`,
  `feeAmount = 0` (MVP chưa thu phí). Admin có thể bật `FEE`/`BLOCK` qua
  `cancellation_policy` (DB) hoặc `LATE_CANCEL_ACTION`.
- Huỷ trễ bị chặn (`BLOCK`) → `409 LATE_CANCEL_BLOCKED`.
- Lịch đã qua giờ và khách tự huỷ → `400 APPOINTMENT_PASSED`.
- Huỷ lần 2 → `409 BOOKING_ALREADY_CANCELLED`; lịch terminal khác → `409 BOOKING_TERMINAL`.
- Tham số `?reason=` (optional) được ghi vào `booking_status_history` + audit log.
- Admin/staff huỷ hộ luôn bỏ qua cutoff; phí late chỉ tính cho khách.

#### Đổi lịch — reschedule (S2-02)

```json
// PATCH /bookings/101/reschedule
{ "date": "2026-11-25", "time": "14:00",
  "serviceIds": [2, 1],        // optional — bỏ trống giữ nguyên
  "staffId": 1,                 // optional — bỏ trống giữ thợ cũ
  "reason": "Kulanz",           // bắt buộc khi staff override cutoff
  "note": "..." }               // optional
```

- Khách chỉ đổi được khi còn ≥ 24h; trong 24h → `400 RESCHEDULE_TOO_LATE`.
- Staff được override nhưng **bắt buộc** `reason`, thiếu → `400 REASON_REQUIRED`.
- Mỗi lần đổi lưu snapshot lịch cũ vào `booking_version`; response trả `versionNo` (1, 2, …).
- Lịch terminal → `409 BOOKING_TERMINAL`.

#### No-show (S2-05)

```json
// PATCH /bookings/101/no-show
{ "reason": "Telefonisch bestätigt", "override": true }
```

- Mặc định chỉ đánh dấu được **sau** giờ hẹn; trước giờ → `409 NO_SHOW_TOO_EARLY`.
- Staff override trước giờ phải gửi `override: true` + `reason`, thiếu → `400 REASON_REQUIRED`.
- Đánh dấu lại cùng trạng thái là idempotent (200, không tạo history trùng).
- Khách vượt ngưỡng no-show bị chặn đặt lịch mới (xem POST `/bookings`).

#### Đổi trạng thái (S2-01)

- Ma trận: `bestätigt` → `eingecheckt` / `in Bearbeitung` / `abgeschlossen` / `storniert` /
  `nicht erschienen`; `eingecheckt` → `in Bearbeitung` / `storniert` / `nicht erschienen`;
  `in Bearbeitung` → `bestätigt` / `abgeschlossen` / `storniert`.
- Check-in quá sớm (ngoài `CHECK_IN_WINDOW_HOURS`) → `409 CHECK_IN_TOO_EARLY`.
- Đóng lịch chưa tới giờ → `409 COMPLETION_TOO_EARLY`.
- Transition cấm hoặc lịch terminal → `409 INVALID_STATUS_TRANSITION` / `409 BOOKING_TERMINAL`.
- Mọi thay đổi ghi `booking_status_history` (from/to, actor, role, reason, thời điểm).

#### Tra cứu booking của guest (S2-08)

```json
// Bước 1 — POST /bookings/lookup
{ "confirmationCode": "TT-7K3M9Q", "phone": "0176 12345678" }
// → data: { challengeId, expiresAt, attemptsRemaining, otp?, message }
//   otp chỉ có khi GUEST_LOOKUP_EXPOSE_OTP=true (dev/test)

// Bước 2 — POST /bookings/lookup/verify
{ "challengeId": "uuid", "otp": "123456" }
// → data: { booking: {...}, otherBookings: [] }
```

- Sai confirmation code **hoặc** SĐT → cùng lỗi `400 INVALID_CONFIRMATION_CODE`
  (không tiết lộ booking/người khác có tồn tại hay không).
- Sai OTP → `400 INVALID_OTP`; sai quá 5 lần → `409 OTP_TOO_MANY_ATTEMPTS`; quá hạn → `400 OTP_EXPIRED`.
- Quá 5 request / 15 phút / SĐT → `429 TOO_MANY_REQUESTS`.
- OTP lưu dạng SHA-256 salted theo `challengeId`, **dùng 1 lần**.

## 4b. Waitlist (S2-04)

| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| POST | `/waitlist` | public | `{ serviceId, desiredDate, desiredStartTime?, name, phone, email?, note? }` |
| GET | `/waitlist?date=&serviceId=&status=&page=&size=` | ADMIN/MANAGER/RECEPTIONIST | `PageResponse` |
| DELETE | `/waitlist/:id?phone=...` | public (đúng SĐT) hoặc staff | Huỷ entry, status `CANCELLED` |

```json
// Response → data
{ "id": 9, "serviceId": 2, "serviceName": "Pediküre",
  "desiredDate": "2026-11-25", "desiredStartTime": null,
  "name": "Anna Müller", "phone": "0176 12345678", "email": "anna@test.de",
  "status": "ACTIVE", "notifiedAt": null, "createdAt": "2026-11-19T10:00:00" }
```

- `status`: `ACTIVE` | `NOTIFIED` | `CANCELLED` | `EXPIRED`.
- Mỗi `SĐT + dịch vụ + ngày` chỉ có **1 entry ACTIVE**; trùng → `409 WAITLIST_DUPLICATE`.
- Khi booking cùng ngày/dịch vụ bị huỷ hoặc đổi lịch và slot thực sự còn trống,
  các entry ACTIVE được chuyển `NOTIFIED` + gửi thông báo (adapter log ở dev).
- `activeKey` được set `NULL` sau khi notify/huỷ → khách có thể đăng ký lại.

## 4c. Audit log (S2-06)

### GET `/audit` (ADMIN)

Query: `entityType`, `entityId`, `action`, `actorId`, `from`, `to` (ISO date-time),
`page` (mặc định 0), `size` (mặc định 20). Trả `PageResponse`.

```json
// data.content[0]
{ "id": 42, "actorId": "uuid", "actorEmail": "admin@gmail.com", "actorRole": "ADMIN",
  "action": "BOOKING_CANCEL", "entityType": "BOOKING", "entityId": "101",
  "beforeJson": "{...}", "afterJson": "{...}", "reason": "Kundenwunsch",
  "ip": "127.0.0.1", "traceId": "8f14e45f-...", "createdAt": "2026-11-19T10:15:30" }
```

- Append-only, ghi trong cùng transaction nghiệp vụ.
- Action hiện có: `BOOKING_CREATE`, `BOOKING_CANCEL`, `BOOKING_STATUS_UPDATE`,
  `BOOKING_NO_SHOW`, `BOOKING_RESCHEDULE` (+ các action `@Audited` khác).

## 4d. Phân trang & filter admin list (S2-09)

`GET /bookings` — không có `page` → **array** như S1; có `page` → `PageResponse`:

```
GET /bookings?page=0&size=20&status=bestätigt&staffId=1&serviceId=2
             &from=2026-11-19&to=2026-11-30&q=anna&sort=startAt,asc
```

- `status`: chuỗi trạng thái tiếng Đức (đúng như response).
- `from`/`to`: ngày `yyyy-MM-dd`, áp lên `start_at` (fallback `preferredDateTime`).
- `q`: tìm trong tên, SĐT, email.
- `sort`: whitelist `preferredDateTime|startAt|status|id` + `,asc|,desc` (mặc định `preferredDateTime,desc`).
- `size` tối đa 100.

`GET /customers?page=0&size=20` tương tự; phần tử có thêm `noShowCount`.

## 5. Staff & lịch làm việc (Sprint 1)

| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/staff?includeInactive=false` | ADMIN/MANAGER/RECEPTIONIST | Danh sách thợ + kỹ năng |
| POST | `/staff` | ADMIN/MANAGER | Tạo thợ |
| GET | `/staff/:id` | ADMIN/MANAGER/RECEPTIONIST | Chi tiết thợ |
| PUT | `/staff/:id` | ADMIN/MANAGER | Cập nhật; `skills != null` thay toàn bộ kỹ năng |
| DELETE | `/staff/:id` | ADMIN/MANAGER | Soft delete (`active = false`) |
| GET | `/staff/:id/skills` | như GET `/staff/:id` | Danh sách kỹ năng |
| PUT | `/staff/:id/skills` | ADMIN/MANAGER | Body: `[{ "serviceId": 1, "level": "ADVANCED" }]` |
| GET | `/staff/:id/shifts?from=&to=` | ADMIN/MANAGER/RECEPTIONIST hoặc chính thợ | Ca làm |
| POST | `/staff/:id/shifts` | ADMIN/MANAGER | `{ "workDate", "startTime", "endTime" }`; chồng lấn → `409 SHIFT_OVERLAP` |
| DELETE | `/staff/:id/shifts/:shiftId` | ADMIN/MANAGER | Xoá ca |
| GET | `/staff/:id/time-off` | ADMIN/MANAGER/RECEPTIONIST hoặc chính thợ | Nghỉ phép |
| POST | `/staff/:id/time-off` | ADMIN/MANAGER hoặc chính thợ | `{ "startAt", "endAt", "reason"? }` |
| DELETE | `/staff/:id/time-off/:timeOffId` | ADMIN/MANAGER | Huỷ (status `CANCELLED`) |
| GET | `/staff/:id/bookings` | ADMIN/MANAGER/RECEPTIONIST hoặc chính thợ | Lịch của thợ |
| GET | `/staff/me` | NAIL_TECH (mọi role đã đăng nhập có hồ sơ thợ) | Hồ sơ thợ của mình |
| GET | `/staff/me/bookings` | NAIL_TECH | **Chỉ** lịch của chính mình (S1-07) |
| GET | `/staff/me/shifts` | NAIL_TECH | Ca của mình |
| GET/POST | `/staff/me/time-off` | NAIL_TECH | Xem/tự đăng ký nghỉ |

`staff` shape:
```json
{
  "id": 1,
  "displayName": "Minh",
  "color": "#E91E63",
  "active": true,
  "commissionRate": 0.4,
  "branchId": 1,
  "userId": null,                       // UUID tài khoản đăng nhập (optional)
  "skills": [
    { "serviceId": 1, "serviceName": "Maniküre", "duration": 45, "price": 28.0, "level": "ADVANCED" }
  ]
}
```
`level`: `BASIC` | `ADVANCED` | `EXPERT`. `Shift`: `{ id, staffId, workDate, startTime, endTime }`.
`TimeOff`: `{ id, staffId, startAt, endAt, reason, status }` với `status` = `ACTIVE` | `CANCELLED`.

> Seed sẵn 3 thợ (Minh/Linh/Hoa) có kỹ năng mọi dịch vụ + ca 09:00–18:00 Mon–Sat trong 14 ngày.
> Chủ nhật nghỉ (D5) → `/availability` trả rỗng.

## 6. Customers (admin)

### GET `/customers`
```json
// Response → data (không page = array; có page = PageResponse)
[{
  "id": "3f2c...-uuid",
  "name": "Anna Müller",
  "telefon": "017612345678",
  "email": "anna.m@email.de",
  "letzterBesuch": "10.04.2026",   // null nếu chưa có booking
  "besuche": 8,
  "noShowCount": 1                 // S2-05: số lần "nicht erschienen"
}]
```

## 7. Stats (admin)

### GET `/stats/dashboard`
```json
// Response → data
{
  "todayBookings": 12,
  "monthlyRevenue": 4850.0,
  "totalCustomers": 342,
  "topService": "Shellac / Gellack",
  "bookingsTrend": "+2",
  "revenueTrend": "+12,5%"
}
```
Doanh thu = tổng giá dịch vụ các booking trong tháng hiện tại (bỏ qua `storniert`);
trend so sánh hôm nay/hôm qua và tháng này/tháng trước.

---

## Endpoint cũ

- `POST /auth/introspect|refresh|logout` — vẫn dùng được.
- `/users` CRUD (ADMIN) — vẫn dùng được; **GET `/users` đã đổi sang DTO + phân trang**
  (không còn trả entity/password/roles). S1: `POST /users` yêu cầu ADMIN (không còn public).
- `/booking/**` — **@Deprecated**, chỉ giữ 1 sprint để tương thích Postman.
  Frontend mới dùng `/bookings/**`. S1 đã đưa các endpoint này qua **cùng luồng**
  availability/locking nên booking legacy vẫn có `staff_id`, `start_at`, `end_at`,
  `buffer_end_at` và snapshot giá.
- `GET /bookings/availability` — **@Deprecated**, dùng `GET /availability`.

## Chạy thử

```bash
docker compose up --build     # Flyway V1+V2+V3 tự migrate; MySQL có healthcheck
# hoặc local: ./mvnw spring-boot:run (cần MySQL + biến JWT_SECRET_KEY)
# login nhanh:
curl -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"emailOrPhone":"admin@gmail.com","password":"<ADMIN_PASSWORD>"}'

# xem slot 2 dịch vụ liên tiếp (60' + 45'):
curl "http://localhost:8080/availability?date=2026-11-20&serviceIds=2,1"

# đặt lịch có Idempotency-Key:
curl -X POST http://localhost:8080/bookings -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"serviceId":2,"date":"2026-11-25","time":"09:00","name":"Anna Müller",
       "phone":"0176 12345678","email":"anna@test.de"}'
```

## Test tự động

- `AvailabilityEngineTest` — **42 unit test** cho engine (buffer, ca gãy, nghỉ phép, skill,
  timezone, cutoff, grid slot, multi-service, biên thời gian).
- `AvailabilityApiTest` — 10 test API `/availability` (public, filter thợ, nghỉ phép,
  booking đã có, Chủ nhật, lỗi tham số).
- `StaffApiTest` — 10 test CRUD thợ/kỹ năng/ca/nghỉ phép + chặn chồng lấn.
- `BookingFlowTest` — 7 test luồng đặt lịch S1 (2 dịch vụ liên tiếp 60'+45', snapshot giá,
  chặn trùng giờ, fail do thiếu skill, auto-assign).
- `RbacMatrixTest` — 7 test ma trận phân quyền (thợ chỉ thấy lịch mình,
  RECEPTIONIST chỉ đọc, MANAGER không quản trị user, anonymous rules).
- `BookingConcurrencyTest` — **Testcontainers MySQL**: 20 thread cùng thợ + cùng slot →
  đúng 1 thành công / 19 `SLOT_TAKEN`; nhiều thợ → số thành công = số thợ; multi-service
  giữ đủ `buffer_end`.
- `ApiContractSmokeTest` / `ErrorContractTest` — contract cũ (register → booking → cancel → 401/403).
- `FlywayMigrationTest` — chạy V1+V2+V3 trên DB trống (H2 MODE=MySQL), seed branch.
- **Sprint 2:**
  - `Sprint2BookingPolicyTest` (10) — status/history/terminal, cancel free/late, reschedule
    cutoff + version, check-in sớm, no-show override + chặn no-show.
  - `GuestLookupTest` (6) — OTP 2 bước, không lộ dữ liệu, rate limit, giới hạn lần nhập.
  - `IdempotencyTest` (3) — replay, conflict payload, không key.
  - `WaitlistTest` (3) — chống trùng, notify khi giải phóng slot, phân quyền.
  - `Sprint2AdminApiTest` (3) — audit log + RBAC, paging/filter/sort.

Chạy: `./mvnw verify` (H2 cho test thường; `BookingConcurrencyTest` cần Docker).
Trên Windows + Docker Desktop, nếu Testcontainers không tìm thấy daemon, thêm vào
`%USERPROFILE%\.testcontainers.properties`:

```properties
docker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy
docker.host=npipe:////./pipe/dockerDesktopLinuxEngine
```

hoặc đặt biến môi trường `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`.
