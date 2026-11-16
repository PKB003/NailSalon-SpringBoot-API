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
- Flyway tự chạy `V1__init.sql` trước khi Hibernate khởi động (`ddl-auto=none`).

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

- `ApiContractSmokeTest` — luồng contract đầy đủ (H2).
- `ErrorContractTest` — envelope lỗi `{code, errorCode, message, details, traceId, timestamp}`,
  401/403/404/415, Swagger `/v3/api-docs`.
- `FlywayMigrationTest` — chạy migration V1 trên DB trống (H2 MODE=MySQL).
- `BookingConcurrencyTest` — Testcontainers MySQL, 20 thread. Test chứng minh lỗi B-03
  (double-booking khi slot trống) đang `@Disabled` và sẽ bật sau S1-05.
  Không có Docker → tự skip (`disabledWithoutDocker = true`).
- JaCoCo gate: instruction coverage ≥ 25% ở Sprint 0, nâng dần mỗi sprint.

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
- [fiskaly SIGN DE — sandbox & tích hợp](docs/tse/fiskaly-setup.md)
- [Plan.md](Plan.md) — phân tích, kiến trúc, roadmap, test strategy, runbook thuế/TSE.

## CI

`.github/workflows/ci.yml`: JDK 17 → `./mvnw verify` (test + coverage gate) →
upload báo cáo → dependency review trên PR → build & push image GHCR khi push `main`.
