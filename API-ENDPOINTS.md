# 📡 API Endpoints — Hướng dẫn cho Frontend

> **Backend đã hoàn thiện đầy đủ các endpoint trong API Contract.** Tài liệu này mô tả
> shape JSON **thực tế** mà backend trả về, kèm ví dụ request/response.

## Cấu hình chung

- **Base URL:** đặt `VITE_API_URL=http://localhost:8080` (backend chạy port 8080, KHÔNG có prefix `/api`).
  Nếu frontend tự nối thêm đường dẫn kiểu `${VITE_API_URL}/auth/login` thì dùng giá trị trên.
- **CORS:** đã mở sẵn cho `http://localhost:3000` và `http://localhost:5173`
  (có thể đổi qua property `app.cors.allowed-origins`).
- **Auth:** gửi header `Authorization: Bearer <token>` cho mọi endpoint (trừ đăng nhập/đăng ký,
  GET `/services` và GET `/bookings/availability` — 2 endpoint này public).
- **401** (token thiếu/hết hạn/logout) → frontend tự xóa token, về `/auth`.
- **403** (đã đăng nhập nhưng không đủ quyền — ví dụ gọi endpoint admin bằng token client).

### Envelope response

Mọi response có dạng `{ code, message, timeStamp, data }`. Hàm `unwrap` phía frontend
đọc được payload qua **`data`** (backend vẫn serialize song song trường `result` cũ để
tương thích Postman/test — nội dung giống hệt nhau).

```json
{ "code": 200, "message": "Success", "timeStamp": "2026-04-17T10:30:00", "data": { ... } }
```

Lỗi trả về dạng `{ "code": ..., "message": "..." }` (HTTP status khớp: 400/401/403/404/409).

### Tài khoản admin mặc định (seed tự động)

`emailOrPhone: admin@gmail.com` — `password: 12345678`

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
    "role": "client",              // "client" | "admin" → điều hướng /client hay /admin
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

## 3. Bookings

### GET `/bookings/availability?date=YYYY-MM-DD`
```json
// Response → data
{ "slots": ["09:00", "09:30", "10:00", ...] }   // chỉ slot còn trống
```
Giờ mở cửa 09:00–18:00, bước 30 phút; slot trong quá khứ bị ẩn; ngày quá khứ → mảng rỗng.

### POST `/bookings` (public — có token sẽ tự gắn user, không token vẫn đặt được)
```json
// Request
{ "serviceId": 3, "date": "2026-04-17", "time": "14:30",
  "name": "Anna Müller", "phone": "0176 12345678",
  "email": "anna.m@email.de", "note": "Wünsche zum Design" }
```
```json
// Response → data
{
  "id": 101,                 // ← dùng làm mã xác nhận
  "serviceId": 3,
  "service": "Shellac / Gellack",
  "date": "17.04.2026",      // dd.MM.yyyy
  "time": "14:30",           // HH:mm
  "name": "Anna Müller",
  "phone": "0176 12345678",
  "email": "anna.m@email.de",
  "note": "Wünsche zum Design",
  "employee": null,
  "status": "in Bearbeitung",
  "price": "35 €"
}
```

### Booking shape chung
Backend trả **song song field tiếng Anh (client view) và tiếng Đức (admin view)** trong cùng
1 object nên cả 2 view dùng chung shape:
- `id`, `serviceId`, `phone`, `email`, `note`
- client view: `service`, `date`, `time`, `name`, `employee`, `status`, `price` (`"35 €"`)
- admin view: `dienstleistung`, `datum`, `uhrzeit`, `kunde`, `mitarbeiter` (giá trị trùng các field tiếng Anh)
- `status`: `bestätigt` | `in Bearbeitung` | `abgeschlossen` | `storniert` (booking mới luôn `in Bearbeitung`)

| Method | Endpoint | Quyền | Response |
|---|---|---|---|
| GET | `/bookings/my` | token khách | `booking[]` của khách, mới nhất trước |
| GET | `/bookings` | admin | `booking[]` toàn bộ, mới nhất trước |
| PATCH | `/bookings/:id/cancel` | chủ booking hoặc admin | booking (`status = "storniert"`). **Chính sách 24h:** khách chỉ tự hủy được khi còn ≥ 24h trước giờ hẹn; trong vòng 24h → `400 { message: "Stornierung nur bis 24 Stunden vor dem Termin möglich, bitte anrufen" }` (frontend hiện tooltip "bitte anrufen"). Admin được phép hủy mọi lúc (trừ lịch đã `abgeschlossen`/`storniert` — terminal, bất biến). Lịch đã qua giờ → `400 APPOINTMENT_PASSED`; cancel 2 lần → `409`; cancel lịch đã hoàn thành → `409`. |
| PATCH | `/bookings/:id/status` | admin | booking — body `{ "status": "...", "employee": "Minh"? }`. Tuân **ma trận transition**: `in Bearbeitung` ↔ `bestätigt`, cả 2 → `abgeschlossen`/`storniert`; `abgeschlossen`/`storniert` là **terminal** (khóa mọi transition kể cả admin, bảo vệ doanh thu). Đóng lịch chưa tới giờ → `409 COMPLETION_TOO_EARLY`; transition cấm → `409 INVALID_STATUS_TRANSITION`. |

## 4. Customers (admin)

### GET `/customers`
```json
// Response → data
[{
  "id": "3f2c...-uuid",
  "name": "Anna Müller",
  "telefon": "017612345678",
  "email": "anna.m@email.de",
  "letzterBesuch": "10.04.2026",   // null nếu chưa có booking
  "besuche": 8
}]
```

## 5. Stats (admin)

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

## Endpoint cũ (vẫn hoạt động, giữ tương thích Postman)

`POST /auth/introspect|refresh|logout`, `/users` CRUD (admin), `/booking`, `/booking/guest`,
`/booking/user/{userId}`... — frontend mới nên dùng endpoint bên trên.

## Chạy thử

```bash
docker-compose up --build     # hoặc: mvnw spring-boot:run (cần MySQL)
# login nhanh:
curl -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"emailOrPhone":"admin@gmail.com","password":"12345678"}'
```

## Test tự động

`ApiContractSmokeTest` (src/test/java/.../contract/) chạy trọn luồng contract:
register → login → me → password → services → availability → booking → my →
bookings/customers/stats (admin) → status → cancel → kiểm tra 401/403.
Chạy: `mvnw test` (dùng H2 in-memory, không cần MySQL).

