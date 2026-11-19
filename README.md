# NailSalon Booking API (Spring Boot)

Backend cho tiệm nail tại Đức: đặt lịch, quản trị khách hàng, dịch vụ, thống kê.
Định hướng sắp tới: POS + hoá đơn §14 UStG, DATEV, TSE fiskaly (xem [Plan.md](Plan.md)).

## Stack

Java 17 · Spring Boot 3.2 · Spring Security (JWT) · Spring Data JPA · MySQL 8 ·
Flyway · springdoc OpenAPI · Testcontainers · JaCoCo · Docker multi-stage.

## Chạy nhanh với Docker

```bash
cp .env.example .env          # điền MYSQL_ROOT_PASSWORD + JWT_SECRET_KEY + ADMIN_PASSWORD
docker compose up --build
```

- App: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health (Docker healthcheck dùng endpoint này)
- MySQL có healthcheck; app chỉ start khi MySQL healthy.
- Flyway tự chạy `V1__init.sql` + `V2__sprint1_staff_availability.sql` +
  `V3__sprint2_booking_v2.sql` trước khi Hibernate khởi động (`ddl-auto=none`).
- Lần chạy đầu seed: admin, 6 dịch vụ mẫu, 1 chi nhánh `Europe/Berlin`, 3 thợ
  (Minh/Linh/Hoa) + kỹ năng + ca 09:00–18:00 Mon–Sat trong 14 ngày.

> **Đổi `MYSQL_ROOT_PASSWORD` sau lần chạy đầu?** MySQL chỉ dùng biến này khi khởi tạo
> volume. Nếu đổi, phải `docker compose down -v` (xoá dữ liệu dev) rồi `up` lại, nếu không
> app sẽ báo `Access denied for user 'root'`.
>
> Không muốn tạo `.env`? Truyền biến inline, nhưng phải đủ **cả** `MYSQL_ROOT_PASSWORD`
> và `SPRING_DATASOURCE_PASSWORD` (nếu thiếu, Compose dùng mặc định `root` và sẽ recreate
> container với sai mật khẩu).

## Chạy local (không Docker)

```bash
# Yêu cầu: JDK 17+, MySQL 8
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/ttnails?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=root
export JWT_SECRET_KEY="$(openssl rand -base64 64 | tr -d '\n')"
export ADMIN_PASSWORD="change-me"
./mvnw spring-boot:run
```

Trên Windows PowerShell dùng `$env:JWT_SECRET_KEY="..."` thay cho `export`.

## Test & coverage

```bash
./mvnw verify
```

- `AvailabilityEngineTest` — 42 unit test cho engine slot/buffer/ca/nghỉ phép/skill/timezone/cutoff.
- `AvailabilityApiTest` (10) + `StaffApiTest` (10) — API availability và CRUD thợ/ca/nghỉ phép.
- `BookingFlowTest` (7) — đặt 2 dịch vụ liên tiếp 60'+45' với 1 thợ, snapshot giá, chặn trùng giờ.
- `RbacMatrixTest` (7) — ma trận phân quyền S1 (thợ chỉ thấy lịch mình, lễ tân/quản lý).
- `ApiContractSmokeTest` / `ErrorContractTest` — contract cũ + envelope lỗi + `/v3/api-docs`.
- `FlywayMigrationTest` — chạy migration V1+V2+V3 trên DB trống (H2 MODE=MySQL).
- `BookingConcurrencyTest` — Testcontainers MySQL, 20 thread: cùng thợ + cùng slot →
  đúng **1** thành công, còn lại `409 SLOT_TAKEN`; không có Docker → tự skip.
- **Sprint 2:**
  - `Sprint2BookingPolicyTest` (10) — state machine/history, cancel free/late (WARN),
    reschedule cutoff + version, no-show override/idempotent, chặn no-show + admin override.
  - `GuestLookupTest` (6) — OTP 2 bước, sai code/SĐT không lộ dữ liệu, rate limit 429,
    giới hạn số lần nhập, OTP dùng 1 lần.
  - `IdempotencyTest` (3) — replay cùng key, cùng key khác payload → 409, không key → 2 booking.
  - `WaitlistTest` (3) — chống spam trùng, huỷ booking → NOTIFIED + notify, public/staff auth.
  - `Sprint2AdminApiTest` (3) — audit log + RBAC, phân trang/filter/sort booking & customer.
- JaCoCo gate: instruction coverage ≥ 25% (S1 thực tế ~73%, line ~77%, branch ~52%).
  Mục tiêu dài hạn theo Plan §7.3: line ≥ 70%, branch ≥ 60%.

## Cấu hình

Mọi cấu hình nhạy cảm đọc từ biến môi trường (xem [.env.example](.env.example)):

| Biến | Bắt buộc | Ghi chú |
|---|---|---|
| `SPRING_DATASOURCE_*` | ✅ | URL/user/password MySQL |
| `JWT_SECRET_KEY` | ✅ | ≥ 64 ký tự; production bắt buộc đổi |
| `ADMIN_PASSWORD` | production | Bỏ trống → sinh ngẫu nhiên, in 1 lần trong log |
| `APP_CORS_ALLOWED_ORIGINS` | | Mặc định localhost:3000, localhost:5173 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | | Mặc định `none`; có thể bật `validate` sau deploy đầu |
| `FREE_CANCEL_HOURS` | | Mặc định `24` — cutoff huỷ/đổi lịch miễn phí (D4) |
| `LATE_CANCEL_ACTION` | | `WARN` (mặc định) \| `FEE` \| `BLOCK` — huỷ trong 24h |
| `LATE_CANCEL_FEE_PERCENT` | | Mặc định `0` — chỉ dùng khi action `FEE` và DB chưa có policy |
| `CHECK_IN_WINDOW_HOURS` | | Mặc định `24` — cửa sổ được check-in trước giờ hẹn |
| `NO_SHOW_MAX_COUNT` | | Mặc định `3` — vượt ngưỡng thì chặn đặt lịch mới |
| `NO_SHOW_WINDOW_DAYS` | | Mặc định `180` — cửa sổ đếm no-show |
| `NO_SHOW_ENFORCEMENT` | | `BLOCK` (mặc định) \| `WARN` |
| `IDEMPOTENCY_TTL_HOURS` | | Mặc định `24` — thời gian giữ `Idempotency-Key` |
| `GUEST_LOOKUP_OTP_TTL_MINUTES` | | Mặc định `10` |
| `GUEST_LOOKUP_MAX_ATTEMPTS` | | Mặc định `5` lần nhập OTP |
| `GUEST_LOOKUP_MAX_REQUESTS` | | Mặc định `5` request / `GUEST_LOOKUP_RATE_WINDOW_MINUTES` (15') |
| `GUEST_LOOKUP_EXPOSE_OTP` | dev/test | `true` để trả OTP trong response; **production phải `false`** |
| `FISKALY_*` | S3 | Placeholder, xem [docs/tse/fiskaly-setup.md](docs/tse/fiskaly-setup.md) |

`src/main/resources/application.properties` chỉ chứa **giá trị dev mặc định**;
secret thật override bằng env. Không commit `.env`.

## API contract

- [`openapi.yaml`](openapi.yaml) — contract v1 đóng băng (OpenAPI 3.1), dùng để generate TS client.
- [`API-ENDPOINTS.md`](API-ENDPOINTS.md) — hướng dẫn frontend + ví dụ JSON.
- `/v3/api-docs` — spec sinh tự động từ code (nguồn sự thật khi có thay đổi).

## Tài liệu vận hành

- [Biên bản chốt D1–D18 (2026-11-14)](docs/decisions/2026-11-14-decision-record.md)
- [Flyway migrate & rollback V1](docs/db/rollback-V1.md)
- [Flyway migrate & rollback V2 (Sprint 1)](docs/db/rollback-V2.md)
- [Flyway migrate & rollback V3 (Sprint 2)](docs/db/rollback-V3.md)
- [Ghi chú Sprint 2 cho frontend (S2-10)](docs/sprint2-frontend-notes.md)
- [fiskaly SIGN DE — sandbox & tích hợp](docs/tse/fiskaly-setup.md)
- [Plan.md](Plan.md) — phân tích, kiến trúc, roadmap, test strategy, runbook thuế/TSE.

## Sprint 1 — Staff & Availability Engine

Đã hoàn thành backend (2026-11-17):

- Schema V2: `branch`, `staff`, `staff_skill`, `staff_shift`, `time_off`, `booking_item`,
  cột thợ/khung giờ/snapshot giá trên `booking`; unique index `uk_booking_staff_start`.
- `GET /availability?date=&serviceIds=&staffId=` (public, engine v2, p95 < 300ms với seed).
- CRUD thợ/kỹ năng/ca/nghỉ phép: `/staff/**` (RBAC mới `NAIL_TECH/RECEPTIONIST/MANAGER`).
- `POST /bookings` nhận `serviceIds` (tối đa 3) + `staffId`; booking lưu
  `staff_id/start_at/end_at/buffer_end_at`, `price_snapshot`, `currency` và `booking_item`.
- Chống double-booking: khóa staff row (id ASC) + locking read trong transaction
  `READ_COMMITTED`; 20 thread → 1 thành công / 19 `SLOT_TAKEN`.

Chi tiết endpoint + ví dụ JSON: [API-ENDPOINTS.md](API-ENDPOINTS.md).
Rollback migration: [docs/db/rollback-V2.md](docs/db/rollback-V2.md).

## Sprint 2 — Booking v2: policy, waitlist, no-show, audit

Đã hoàn thành backend (2026-11-19):

- Schema V3: `booking_status_history`, `booking_version`, `cancellation_policy`,
  `cancellation_record`, `waitlist`, `audit_log`, `idempotency_key`,
  `guest_lookup_challenge`; `booking.confirmation_code` (unique).
- **S2-01** state machine production + history bất biến. Booking mới ở `bestätigt`
  (trước S1 là `in Bearbeitung` → **breaking change** cho frontend S2-10);
  terminal = `abgeschlossen` / `storniert` / `nicht erschienen` / `abgelaufen`.
- **S2-02** `PATCH /bookings/:id/reschedule`: cutoff 24h cho khách, staff override kèm
  `reason`, mỗi lần đổi lưu `booking_version` + tăng `versionNo`.
- **S2-03** huỷ theo policy service/branch: `WARN` (mặc định, vẫn huỷ + ghi late),
  `FEE`, `BLOCK`; trả `cancellation { late, feeAmount, refundAmount, ... }`.
- **S2-04** waitlist công khai + chống spam bằng unique `active_key`; khi slot được
  giải phóng, entry ACTIVE đúng service/ngày được đánh dấu `NOTIFIED` + gửi thông báo.
- **S2-05** `PATCH /bookings/:id/no-show`; đếm no-show theo SĐT/user trong 180 ngày,
  vượt 3 lần → `409 NO_SHOW_BLOCKED`; staff override bằng `overrideNoShow` + audit.
- **S2-06** audit log append-only (ai làm gì, before/after JSON, traceId) + `GET /audit`
  chỉ ADMIN; `@Audited` AOP cho endpoint mới.
- **S2-07** `Idempotency-Key` cho `POST /bookings`: retry cùng key trả nguyên booking cũ,
  cùng key khác payload → `409 IDEMPOTENCY_KEY_REUSED`.
- **S2-08** `POST /bookings/lookup` → `POST /bookings/lookup/verify`: confirmation code +
  SĐT + OTP (SHA-256 salted, dùng 1 lần, rate limit, không lộ dữ liệu người khác).
- **S2-09** `GET /bookings` và `GET /customers`: không `page` → array cũ; có `page` →
  `PageResponse`; filter `status/staffId/serviceId/from/to/q`, sort whitelist.

Chi tiết endpoint + ví dụ JSON: [API-ENDPOINTS.md](API-ENDPOINTS.md).
Rollback migration: [docs/db/rollback-V3.md](docs/db/rollback-V3.md).

## CI

`.github/workflows/ci.yml`: JDK 17 → `./mvnw verify` (test + coverage gate) →
upload báo cáo → dependency review trên PR → build & push image GHCR khi push `main`.
