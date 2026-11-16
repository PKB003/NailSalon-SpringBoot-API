# Flyway baseline V1 — migrate & rollback

## Trạng thái

- Migration: `src/main/resources/db/migration/V1__init.sql`
- Bảng: `user`, `user_roles`, `services`, `booking`, `invalid_token`
- `spring.jpa.hibernate.ddl-auto=none` (staging/prod). Schema do Flyway quản lý.
- `spring.flyway.baseline-on-migrate=true` + `baseline-version=1`: DB cũ đã có bảng
  (tạo bởi `ddl-auto=update`) sẽ được đánh dấu là đã ở V1 thay vì chạy lại.

## Migrate DB trống (local/staging)

```bash
# 1) Chạy MySQL (hoặc docker compose up mysql)
# 2) Trỏ SPRING_DATASOURCE_* tới DB đích rồi:
./mvnw -B flyway:migrate \
  -Dflyway.url="jdbc:mysql://localhost:3306/ttnails?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -Dflyway.user=root \
  -Dflyway.password=root
```

Hoặc đơn giản chạy app: Flyway tự migrate trước khi Hibernate khởi động.

## Kiểm tra

```sql
SELECT version, description, success, installed_on FROM flyway_schema_history;
SHOW TABLES;
```

## Baseline cho DB đang có dữ liệu (dev cũ)

1. Backup: `mysqldump -u root -p ttnails > backup.sql`.
2. Khởi động app một lần với `SPRING_FLYWAY_ENABLED=true`.
3. Flyway tạo `flyway_schema_history` và ghi V1 là baseline (không chạy lại DDL).
4. Chạy `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` **một lần** để xác nhận schema khớp entity.

## Rollback V1

> Chỉ dùng khi rollback khẩn cấp trên môi trường **chưa có dữ liệu giao dịch**.
> Production có dữ liệu thật: restore từ backup, không drop bảng.

```sql
-- Thứ tự đúng vì có FK
DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS invalid_token;
DROP TABLE IF EXISTS services;
DROP TABLE IF EXISTS user;
DELETE FROM flyway_schema_history WHERE version = '1';
```

## Lưu ý cho V2+

- Không sửa file migration đã chạy (checksum sẽ fail). Tạo file mới.
- Mọi thay đổi phá vỡ tương thích: 2 bước (expand → migrate data → contract).
- Tiền sẽ chuyển `DOUBLE` → `DECIMAL(10,2)` ở S3; viết migration có test trên MySQL thật.
