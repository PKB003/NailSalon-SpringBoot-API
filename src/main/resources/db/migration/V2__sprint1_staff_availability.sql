-- =====================================================================
-- V2 — Sprint 1: Staff & Availability Engine (S1-01, S1-06)
--
-- Thêm: branch, staff, staff_skill, staff_shift, time_off, booking_item
-- Sửa: services (buffer/currency/active), booking (staff + start/end + snapshot giá)
--
-- Quy ước:
--   * Tiền snapshot -> DECIMAL(10,2), EUR (S3 mới chuyển toàn bộ service.price)
--   * Thời gian lưu giờ địa phương của branch (Europe/Berlin) cho tới S5
--   * Script chạy được trên MySQL 8 và H2 MODE=MySQL
-- =====================================================================

CREATE TABLE branch (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    name               VARCHAR(255) NOT NULL,
    timezone           VARCHAR(64)  NOT NULL DEFAULT 'Europe/Berlin',
    open_time          TIME         NOT NULL,
    close_time         TIME         NOT NULL,
    slot_step_minutes  INT          NOT NULL DEFAULT 30,
    buffer_minutes     INT          NOT NULL DEFAULT 10,
    phone              VARCHAR(255),
    address            VARCHAR(255),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id)
);

-- MVP 1 chi nhánh (D2). Các bảng nghiệp vụ tham chiếu branch_id từ đây.
INSERT INTO branch (id, name, timezone, open_time, close_time, slot_step_minutes, buffer_minutes,
                    phone, address, active)
VALUES (1, 'TT Nails', 'Europe/Berlin', '09:00:00', '18:00:00', 30, 10,
        '+49 30 1234567', 'Musterstraße 1, 10115 Berlin', TRUE);

CREATE TABLE staff (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          CHAR(36),
    branch_id        BIGINT       NOT NULL,
    display_name     VARCHAR(255) NOT NULL,
    color            VARCHAR(16),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    commission_rate  DECIMAL(5,4) NOT NULL DEFAULT 0.4000,
    PRIMARY KEY (id),
    CONSTRAINT fk_staff_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE SET NULL,
    CONSTRAINT uk_staff_user UNIQUE (user_id)
);

CREATE TABLE staff_skill (
    staff_id    BIGINT      NOT NULL,
    service_id  BIGINT      NOT NULL,
    level       VARCHAR(32) NOT NULL DEFAULT 'BASIC',
    PRIMARY KEY (staff_id, service_id),
    CONSTRAINT fk_staff_skill_staff FOREIGN KEY (staff_id) REFERENCES staff (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_skill_service FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE CASCADE
);

CREATE TABLE staff_shift (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    staff_id   BIGINT NOT NULL,
    work_date  DATE   NOT NULL,
    start_time TIME   NOT NULL,
    end_time   TIME   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_shift_staff FOREIGN KEY (staff_id) REFERENCES staff (id) ON DELETE CASCADE,
    CONSTRAINT uk_shift_staff_date_start UNIQUE (staff_id, work_date, start_time)
);

CREATE INDEX idx_shift_staff_date ON staff_shift (staff_id, work_date);

CREATE TABLE time_off (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    staff_id   BIGINT       NOT NULL,
    start_at   DATETIME(6)  NOT NULL,
    end_at     DATETIME(6)  NOT NULL,
    reason     VARCHAR(255),
    status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    CONSTRAINT fk_time_off_staff FOREIGN KEY (staff_id) REFERENCES staff (id) ON DELETE CASCADE
);

CREATE INDEX idx_time_off_staff_range ON time_off (staff_id, start_at, end_at);

-- Catalog: buffer mỗi dịch vụ (phút), tiền tệ, soft delete
ALTER TABLE services ADD COLUMN buffer_minutes INT NOT NULL DEFAULT 10;
ALTER TABLE services ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';
ALTER TABLE services ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- Booking: gán thợ + khoảng thời gian thực + snapshot giá (S1-06)
ALTER TABLE booking ADD COLUMN staff_id BIGINT;
ALTER TABLE booking ADD COLUMN start_at DATETIME(6);
ALTER TABLE booking ADD COLUMN end_at DATETIME(6);
ALTER TABLE booking ADD COLUMN buffer_end_at DATETIME(6);
ALTER TABLE booking ADD COLUMN price_snapshot DECIMAL(10,2);
ALTER TABLE booking ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';

ALTER TABLE booking
    ADD CONSTRAINT fk_booking_staff FOREIGN KEY (staff_id) REFERENCES staff (id) ON DELETE SET NULL;

-- Chống double-booking chính xác cùng thợ + cùng giờ bắt đầu.
-- (Overlap lệch giờ được chặn bằng khóa staff row + check overlap ở BookingService.)
CREATE UNIQUE INDEX uk_booking_staff_start ON booking (staff_id, start_at);
CREATE INDEX idx_booking_staff_range ON booking (staff_id, start_at, end_at);

CREATE TABLE booking_item (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    booking_id  BIGINT        NOT NULL,
    service_id  BIGINT        NOT NULL,
    staff_id    BIGINT,
    start_at    DATETIME(6)   NOT NULL,
    end_at      DATETIME(6)   NOT NULL,
    duration    INT           NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    currency    VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    PRIMARY KEY (id),
    CONSTRAINT fk_booking_item_booking FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_item_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT fk_booking_item_staff FOREIGN KEY (staff_id) REFERENCES staff (id) ON DELETE SET NULL
);

CREATE INDEX idx_booking_item_booking ON booking_item (booking_id);
CREATE INDEX idx_booking_item_staff_range ON booking_item (staff_id, start_at);
