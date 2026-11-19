# Flyway V3 (Sprint 2) — Booking v2: policy, waitlist, no-show, audit

## Trạng thái

- Migration: `src/main/resources/db/migration/V3__sprint2_booking_v2.sql`
- Bảng mới:
  - `booking_status_history` — lịch sử mọi transition (S2-01)
  - `booking_version` — snapshot mỗi lần đổi lịch (S2-02)
  - `cancellation_policy` — cutoff + hành vi late-cancel (S2-03, D4)
  - `cancellation_record` — ai huỷ, lý do, phí/hoàn, policy snapshot (S2-03)
  - `waitlist` — đăng ký chờ slot + unique chống spam (S2-04)
  - `audit_log` — append-only before/after JSON (S2-06)
  - `idempotency_key` — chống tạo trùng booking khi retry (S2-07)
  - `guest_lookup_challenge` — OTP tra cứu booking của guest (S2-08)
- Cột mới: `booking.confirmation_code` (`VARCHAR(16)`, unique, backfill `TT-<id 6 số>`).
- Index mới: `uk_booking_confirmation_code`, `idx_booking_status_start`,
  `idx_bsh_booking`, `uk_bv_booking_version`, `idx_cp_branch_service`, `idx_cr_booking`,
  `uk_wl_active`, `idx_wl_lookup`, `idx_audit_entity`, `idx_audit_created`,
  `idx_audit_actor`, `uk_idem_scope_key`, `idx_idem_expires`, `uk_glc_challenge`,
  `idx_glc_phone`, `idx_glc_code`.
- Seed: policy mặc định `branch_id = 1`, `service_id = NULL`, 24h / `WARN` / phí 0
  (idempotent — chỉ insert khi chưa có policy active).

> V3 là migration **expand**: chỉ thêm bảng/cột/index, `confirmation_code` được backfill
> ngay trong migration rồi mới thêm unique index. App phiên bản S1 vẫn đọc/ghi được
> (không biết các bảng mới) trong thời gian deploy.

## Migrate

```bash
# 1) Chạy MySQL (hoặc docker compose up mysql)
# 2) Trỏ SPRING_DATASOURCE_* tới DB đích rồi:
./mvnw -B flyway:migrate \
  -Dflyway.url="jdbc:mysql://localhost:3306/ttnails?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -Dflyway.user=root \
  -Dflyway.password=root
```

Hoặc chạy app/`docker compose up --build`: Flyway tự migrate V1 → V2 → V3 trước khi
Hibernate khởi động (`ddl-auto=none`).

## Kiểm tra sau migrate

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank;

SHOW TABLES LIKE 'booking_status_history';
SHOW TABLES LIKE 'waitlist';
SHOW TABLES LIKE 'idempotency_key';

SELECT COUNT(*) FROM booking WHERE confirmation_code IS NULL;   -- phải = 0
SELECT * FROM cancellation_policy;
```

Kiểm tra bằng API:

```bash
# Login admin
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"emailOrPhone":"admin@gmail.com","password":"<ADMIN_PASSWORD>"}' \
  | jq -r .data.token)

# Booking mới phải có status "bestätigt" + confirmationCode
curl -s -X POST http://localhost:8080/bookings \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
  -d '{"serviceId":1,"date":"<ngày làm việc>","time":"09:00","name":"Test",
       "phone":"01760000000","email":"test@test.de"}' | jq '.data | {status, confirmationCode}'

# Lịch sử trạng thái
curl -s http://localhost:8080/bookings/1/history -H "Authorization: Bearer $TOKEN" | jq
```

## Backfill dữ liệu cũ

- `confirmation_code`: migration đã backfill toàn bộ booking cũ bằng `TT-<id>`.
  Booking tạo sau V3 nhận mã ngẫu nhiên dạng `TT-XXXXXX` (unique, retry 10 lần).
- Booking cũ chưa có `booking_status_history` → history bắt đầu từ transition kế tiếp.
  Nếu muốn có mốc ban đầu:

```sql
INSERT INTO booking_status_history (booking_id, from_status, to_status, actor_label,
                                    actor_role, reason, created_at)
SELECT id, NULL,
       COALESCE(status, 'bestätigt'),
       'backfill', 'SYSTEM', 'V3 backfill', NOW(6)
FROM booking b
WHERE NOT EXISTS (SELECT 1 FROM booking_status_history h WHERE h.booking_id = b.id);
```

> Chỉ chạy sau khi backup và kiểm tra trên staging.

## Rollback V3

> Chỉ dùng khi rollback khẩn cấp trên môi trường **chưa có dữ liệu giao dịch**.
> Production: restore từ backup (`mysqldump`) hoặc expand/contract —
> **không drop bảng** đang có dữ liệu audit/waitlist.

```sql
-- Thứ tự đúng vì có FK
DROP TABLE IF EXISTS guest_lookup_challenge;
DROP TABLE IF EXISTS idempotency_key;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS waitlist;
DROP TABLE IF EXISTS cancellation_record;
DROP TABLE IF EXISTS cancellation_policy;
DROP TABLE IF EXISTS booking_version;
DROP TABLE IF EXISTS booking_status_history;

ALTER TABLE booking DROP INDEX idx_booking_status_start;
ALTER TABLE booking DROP INDEX uk_booking_confirmation_code;
ALTER TABLE booking DROP COLUMN confirmation_code;

DELETE FROM flyway_schema_history WHERE version = '3';
```

Sau rollback phải chạy lại code Sprint 1 (không còn entity/endpoint S2). Lưu ý:
booking tạo bởi S2 có `status = 'bestätigt'` — code S1 hiểu chuỗi này nhưng UI cũ
có thể mong đợi `in Bearbeitung`.

## Lưu ý

- Không sửa file migration đã chạy (checksum fail). Mọi thay đổi schema phải là `V4+`.
- `cancellation_policy` seed gắn với `branch_id = 1` (chi nhánh seed ở V2). Nếu DB
  chưa có branch, seed không chạy và app fallback về `app.policy.*` (24h/WARN/0).
- Waitlist chống spam bằng unique `active_key = phone|serviceId|date`; cột này set
  `NULL` khi entry `NOTIFIED`/`CANCELLED` nên khách có thể đăng ký lại.
- `audit_log` là append-only — không cập nhật/xoá bản ghi; chỉ job dọn dữ liệu cũ
  (nếu có) mới được xoá theo retention.
- OTP chỉ lưu `otp_hash` (SHA-256 salted bằng `challengeId`), không lưu plaintext.
