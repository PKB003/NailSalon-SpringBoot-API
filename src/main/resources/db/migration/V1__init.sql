-- =====================================================================
-- V1 — Baseline schema (Sprint 0, S0-04)
-- Nguồn sự thật: các JPA entity hiện có (User, Booking, Service, InvalidToken).
-- Quy ước:
--   * UUID  -> CHAR(36)  (đặt hibernate.type.preferred_uuid_jdbc_type=CHAR)
--   * Tiền  -> DOUBLE ở baseline hiện tại; S3 sẽ chuyển sang DECIMAL(10,2)
--   * Bảng `user` là từ khoá của H2, test profile đã bật NON_KEYWORDS=USER
-- Không dùng ENGINE/CHARSET inline để script chạy được cả trên MySQL và H2 (MODE=MySQL).
-- =====================================================================

CREATE TABLE user (
    id                 CHAR(36)     NOT NULL,
    name               VARCHAR(255),
    phone              VARCHAR(255),
    is_verified_phone  BOOLEAN      NOT NULL DEFAULT FALSE,
    email              VARCHAR(255),
    is_verified_email  BOOLEAN      NOT NULL DEFAULT FALSE,
    password           VARCHAR(255),
    dob                DATE,
    address            VARCHAR(255),
    gender             VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_phone UNIQUE (phone),
    CONSTRAINT uk_user_email UNIQUE (email)
);

-- @ElementCollection Set<String> roles
CREATE TABLE user_roles (
    user_id  CHAR(36)     NOT NULL,
    roles    VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, roles),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
);

CREATE TABLE services (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(255) NOT NULL,
    description   VARCHAR(1000),
    duration      INT,
    price         DOUBLE,
    kategorie     VARCHAR(255),
    beliebtheit   VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE booking (
    id                   BIGINT   NOT NULL AUTO_INCREMENT,
    first_name           VARCHAR(255),
    last_name            VARCHAR(255),
    email                VARCHAR(255),
    phone                VARCHAR(255),
    service              VARCHAR(255),
    service_id           BIGINT,
    status               VARCHAR(255),
    employee             VARCHAR(255),
    preferred_date_time  DATETIME(6),
    note                 VARCHAR(255),
    user_id              CHAR(36),
    PRIMARY KEY (id),
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES user (id)
);

-- Truy vấn availability/capacity: WHERE preferred_date_time >= ? AND < ?
CREATE INDEX idx_booking_preferred_date_time ON booking (preferred_date_time);
CREATE INDEX idx_booking_service_id ON booking (service_id);

-- Token đã logout (blacklist tạm thời thay cho Redis)
CREATE TABLE invalid_token (
    id    VARCHAR(255) NOT NULL,
    time  DATETIME(6),
    PRIMARY KEY (id)
);
