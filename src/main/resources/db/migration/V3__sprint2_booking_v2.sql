-- =====================================================================
-- V3 — Sprint 2: Booking v2 (policy, waitlist, no-show, audit)
--
-- Thêm:
--   * booking.confirmation_code (mã tra cứu cho guest, unique)
--   * booking_status_history  — mọi transition ghi lịch sử
--   * booking_version         — snapshot mỗi lần đổi lịch (không sửa đè)
--   * cancellation_policy     — cutoff 24h + hành vi late-cancel (D4)
--   * cancellation_record     — ai huỷ, lý do, phí/hoàn, policy snapshot
--   * waitlist                — đăng ký chờ slot + unique chống spam
--   * audit_log               — append-only, before/after JSON
--   * idempotency_key         — chống tạo trùng booking khi retry
--   * guest_lookup_challenge  — OTP tra cứu booking của guest
--
-- Quy ước giống V2: chạy được trên MySQL 8 và H2 MODE=MySQL.
-- Migration dạng expand: cột mới nullable hoặc có default, không drop gì.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Booking: mã xác nhận cho khách tra cứu (S2-08)
-- ---------------------------------------------------------------------
ALTER TABLE booking ADD COLUMN confirmation_code VARCHAR(16);

-- Backfill dữ liệu cũ bằng id (deterministic, không trùng)
UPDATE booking SET confirmation_code = CONCAT('TT-', LPAD(id, 6, '0'))
WHERE confirmation_code IS NULL;

ALTER TABLE booking ADD CONSTRAINT uk_booking_confirmation_code UNIQUE (confirmation_code);
CREATE INDEX idx_booking_status_start ON booking (status, start_at);

-- ---------------------------------------------------------------------
-- 2. Lịch sử trạng thái (S2-01)
-- ---------------------------------------------------------------------
CREATE TABLE booking_status_history (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    booking_id   BIGINT       NOT NULL,
    from_status  VARCHAR(64),
    to_status    VARCHAR(64)  NOT NULL,
    actor_id     CHAR(36),
    actor_label  VARCHAR(255),
    actor_role   VARCHAR(32),
    reason       VARCHAR(500),
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bsh_booking FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE
);

CREATE INDEX idx_bsh_booking ON booking_status_history (booking_id, created_at);

-- ---------------------------------------------------------------------
-- 3. Version của booking khi đổi lịch (S2-02)
-- ---------------------------------------------------------------------
CREATE TABLE booking_version (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    booking_id    BIGINT       NOT NULL,
    version_no    INT          NOT NULL,
    reason        VARCHAR(255),
    snapshot_json LONGTEXT     NOT NULL,
    created_by    CHAR(36),
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bv_booking FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE,
    CONSTRAINT uk_bv_booking_version UNIQUE (booking_id, version_no)
);

-- ---------------------------------------------------------------------
-- 4. Chính sách huỷ (D4: 24h, WARN, không phí ở MVP)
-- ---------------------------------------------------------------------
CREATE TABLE cancellation_policy (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    branch_id            BIGINT,
    service_id           BIGINT,
    free_cancel_hours    INT           NOT NULL DEFAULT 24,
    late_cancel_action   VARCHAR(16)   NOT NULL DEFAULT 'WARN',
    late_cancel_fee_type VARCHAR(16)   NOT NULL DEFAULT 'NONE',
    late_cancel_fee_value DECIMAL(10,2) NOT NULL DEFAULT 0,
    no_show_fee_type     VARCHAR(16)   NOT NULL DEFAULT 'NONE',
    no_show_fee_value    DECIMAL(10,2) NOT NULL DEFAULT 0,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    valid_from           DATETIME(6),
    valid_to             DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_cp_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_service FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE CASCADE
);

CREATE INDEX idx_cp_branch_service ON cancellation_policy (branch_id, service_id, active);

-- Policy mặc định toàn chi nhánh (service_id NULL) — idempotent seed
INSERT INTO cancellation_policy (branch_id, service_id, free_cancel_hours, late_cancel_action,
                                 late_cancel_fee_type, late_cancel_fee_value,
                                 no_show_fee_type, no_show_fee_value, active)
SELECT b.id, NULL, 24, 'WARN', 'NONE', 0, 'NONE', 0, TRUE
FROM branch b
WHERE b.id = 1
  AND NOT EXISTS (SELECT 1 FROM cancellation_policy cp
                  WHERE cp.branch_id = b.id AND cp.service_id IS NULL AND cp.active = TRUE);

CREATE TABLE cancellation_record (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    booking_id        BIGINT        NOT NULL,
    requested_by      CHAR(36),
    requested_by_role VARCHAR(32),
    reason            VARCHAR(500),
    late              BOOLEAN       NOT NULL DEFAULT FALSE,
    policy_snapshot   VARCHAR(1000),
    fee_amount        DECIMAL(10,2) NOT NULL DEFAULT 0,
    refund_amount     DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_cr_booking FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE
);

CREATE INDEX idx_cr_booking ON cancellation_record (booking_id);

-- ---------------------------------------------------------------------
-- 5. Waitlist (S2-04)
-- ---------------------------------------------------------------------
CREATE TABLE waitlist (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    branch_id          BIGINT,
    service_id         BIGINT       NOT NULL,
    desired_date       DATE         NOT NULL,
    desired_start_time TIME,
    customer_id        CHAR(36),
    name               VARCHAR(255),
    phone              VARCHAR(64)  NOT NULL,
    email              VARCHAR(255),
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    active_key         VARCHAR(191),
    notified_at        DATETIME(6),
    note               VARCHAR(500),
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wl_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE SET NULL,
    CONSTRAINT fk_wl_service FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE CASCADE,
    CONSTRAINT fk_wl_customer FOREIGN KEY (customer_id) REFERENCES user (id) ON DELETE SET NULL,
    -- Chống spam: mỗi phone + service + ngày chỉ có 1 entry ACTIVE (NULL khi đã notify/cancel)
    CONSTRAINT uk_wl_active UNIQUE (active_key)
);

CREATE INDEX idx_wl_lookup ON waitlist (service_id, desired_date, status);

-- ---------------------------------------------------------------------
-- 6. Audit log (S2-06) — append-only
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    CHAR(36),
    actor_email VARCHAR(255),
    actor_role  VARCHAR(32),
    action      VARCHAR(64)  NOT NULL,
    entity_type VARCHAR(64)  NOT NULL,
    entity_id   VARCHAR(64),
    before_json LONGTEXT,
    after_json  LONGTEXT,
    reason      VARCHAR(500),
    ip          VARCHAR(64),
    trace_id    VARCHAR(64),
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_log (created_at);
CREATE INDEX idx_audit_actor ON audit_log (actor_id);

-- ---------------------------------------------------------------------
-- 7. Idempotency-Key cho POST /bookings (S2-07)
-- ---------------------------------------------------------------------
CREATE TABLE idempotency_key (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    scope         VARCHAR(64)  NOT NULL,
    idem_key      VARCHAR(191) NOT NULL,
    request_hash  VARCHAR(64)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    response_json LONGTEXT,
    created_at    DATETIME(6)  NOT NULL,
    expires_at    DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_idem_scope_key UNIQUE (scope, idem_key)
);

CREATE INDEX idx_idem_expires ON idempotency_key (expires_at);

-- ---------------------------------------------------------------------
-- 8. Guest lookup bằng confirmation code + SĐT + OTP (S2-08)
-- ---------------------------------------------------------------------
CREATE TABLE guest_lookup_challenge (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    challenge_id       CHAR(36)    NOT NULL,
    confirmation_code  VARCHAR(16) NOT NULL,
    phone              VARCHAR(64) NOT NULL,
    otp_hash           VARCHAR(64) NOT NULL,
    attempts           INT         NOT NULL DEFAULT 0,
    verified           BOOLEAN     NOT NULL DEFAULT FALSE,
    expires_at         DATETIME(6) NOT NULL,
    ip                 VARCHAR(64),
    created_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_glc_challenge UNIQUE (challenge_id)
);

CREATE INDEX idx_glc_phone ON guest_lookup_challenge (phone, created_at);
CREATE INDEX idx_glc_code ON guest_lookup_challenge (confirmation_code, created_at);
