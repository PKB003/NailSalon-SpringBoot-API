# Flyway V2 (Sprint 1) — Staff & Availability Engine: migrate & rollback

## Trạng thái

- Migration: `src/main/resources/db/migration/V2__sprint1_staff_availability.sql`
- Bảng mới: `branch`, `staff`, `staff_skill`, `staff_shift`, `time_off`, `booking_item`
- Cột mới:
  - `services`: `buffer_minutes`, `currency`, `active`
  - `booking`: `staff_id`, `start_at`, `end_at`, `buffer_end_at`, `price_snapshot`, `currency`
- Index mới: `uk_booking_staff_start` (unique `staff_id, start_at`),
  `idx_booking_staff_range`, `idx_shift_staff_date`, `idx_time_off_staff_range`,
  `idx_booking_item_booking`, `idx_booking_item_staff_range`
- Seed: 1 chi nhánh `id = 1` (`TT Nails`, `Europe/Berlin`, 09:00–18:00, slot 30', buffer 10').
- `spring.jpa.hibernate.ddl-auto=none` (staging/prod). Schema do Flyway quản lý.

> V2 là migration **expand** (chỉ thêm bảng/cột/index, cột mới đều nullable hoặc có default)
> nên app phiên bản cũ vẫn đọc/ghi được trong thời gian deploy. Không có bước drop.

## Migrate

```bash
# 1) Chạy MySQL (hoặc docker compose up mysql)
# 2) Trỏ SPRING_DATASOURCE_* tới DB đích rồi:
./mvnw -B flyway:migrate \
  -Dflyway.url="jdbc:mysql://localhost:3306/ttnails?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -Dflyway.user=root \
  -Dflyway.password=root
```

Hoặc đơn giản chạy app/`docker compose up --build`: Flyway tự migrate trước khi
Hibernate khởi động.

## Kiểm tra sau migrate

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank;

SHOW TABLES LIKE 'staff%';
SHOW COLUMNS FROM booking LIKE '%_at';
SHOW INDEX FROM booking WHERE Key_name = 'uk_booking_staff_start';
SELECT * FROM branch;
```

Kiểm tra bằng API (public, không cần token):

```bash
curl "http://localhost:8080/availability?date=<ngày làm việc>&serviceIds=1"
# → data.staff phải có 3 thợ seed (Minh/Linh/Hoa), anyAvailable bắt đầu từ 09:00
```

## Backfill dữ liệu cũ (tuỳ chọn)

Booking tạo trước V2 không có `start_at`/`staff_id`. Availability/engine bỏ qua các
booking này (không chặn slot) — chấp nhận được cho dữ liệu dev. Nếu muốn backfill
trên dữ liệu thật:

```sql
-- 1) Khung giờ từ cột legacy (nếu preferred_date_time có giá trị)
UPDATE booking
SET start_at = preferred_date_time
WHERE start_at IS NULL AND preferred_date_time IS NOT NULL;

-- 2) Gán thợ mặc định (thợ đầu tiên đang active) và tính end/buffer theo dịch vụ
--    CHỈ chạy sau khi đã backup và kiểm tra trên staging.
UPDATE booking b
JOIN services s ON s.id = b.service_id
JOIN staff st ON st.active = TRUE
SET b.staff_id = st.id,
    b.end_at = b.start_at + INTERVAL s.duration MINUTE,
    b.buffer_end_at = b.start_at + INTERVAL (s.duration + s.buffer_minutes) MINUTE
WHERE b.start_at IS NOT NULL
  AND b.staff_id IS NULL;
```

> Cẩn thận: bước 2 có thể tạo trùng `(staff_id, start_at)` → unique index sẽ chặn.
> Kiểm tra trước bằng:
> `SELECT staff_id, start_at, COUNT(*) FROM booking GROUP BY staff_id, start_at HAVING COUNT(*) > 1;`

## Rollback V2

> Chỉ dùng khi rollback khẩn cấp trên môi trường **chưa có dữ liệu giao dịch**.
> Production: restore từ backup (`mysqldump`) hoặc dùng chiến lược expand/contract —
> **không drop bảng** đang có dữ liệu booking.

```sql
-- Thứ tự đúng vì có FK
DROP TABLE IF EXISTS booking_item;
DROP INDEX uk_booking_staff_start ON booking;
DROP INDEX idx_booking_staff_range ON booking;
ALTER TABLE booking DROP FOREIGN KEY fk_booking_staff;
ALTER TABLE booking
    DROP COLUMN staff_id,
    DROP COLUMN start_at,
    DROP COLUMN end_at,
    DROP COLUMN buffer_end_at,
    DROP COLUMN price_snapshot,
    DROP COLUMN currency;
ALTER TABLE services
    DROP COLUMN buffer_minutes,
    DROP COLUMN currency,
    DROP COLUMN active;
DROP TABLE IF EXISTS time_off;
DROP TABLE IF EXISTS staff_shift;
DROP TABLE IF EXISTS staff_skill;
DROP TABLE IF EXISTS staff;
DROP TABLE IF EXISTS branch;
DELETE FROM flyway_schema_history WHERE version = '2';
```

Sau rollback phải chạy lại app với code Sprint 0 (không còn entity/endpoint S1).

## Lưu ý

- Không sửa file migration đã chạy (checksum fail). Mọi thay đổi schema phải là `V3+`.
- `booking.currency` và `services.currency` là `VARCHAR(3) NOT NULL DEFAULT 'EUR'`
  nên an toàn với dữ liệu cũ.
- Thời gian đang lưu **giờ địa phương branch** (`Europe/Berlin`); chuyển UTC ở S5
  sẽ cần migration riêng + backfill theo timezone.
- `services.price` vẫn `DOUBLE`; snapshot giá (`booking.price_snapshot`,
  `booking_item.price`) đã là `DECIMAL(10,2)`. S3 sẽ chuyển toàn bộ tiền sang DECIMAL.
