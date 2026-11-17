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
- Flyway tự chạy `V1__init.sql` + `V2__sprint1_staff_availability.sql` trước khi Hibernate
  khởi động (`ddl-auto=none`).
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
- `FlywayMigrationTest` — chạy migration V1+V2 trên DB trống (H2 MODE=MySQL).
- `BookingConcurrencyTest` — Testcontainers MySQL, 20 thread: cùng thợ + cùng slot →
  đúng **1** thành công, còn lại `409 SLOT_TAKEN`; không có Docker → tự skip.
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

## CI

`.github/workflows/ci.yml`: JDK 17 → `./mvnw verify` (test + coverage gate) →
upload báo cáo → dependency review trên PR → build & push image GHCR khi push `main`.
