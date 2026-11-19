package com.ttnails.booking_service.idempotency.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Bản ghi idempotency (S2-07): khóa theo (scope, key), lưu hash request
 * và response JSON để trả lại y nguyên khi client retry.
 */
@Entity
@Table(name = "idempotency_key",
        uniqueConstraints = @UniqueConstraint(name = "uk_idem_scope_key",
                columnNames = {"scope", "idem_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    /** Đang xử lý — request khác cùng key phải chờ/409. */
    public static final String PROCESSING = "PROCESSING";
    /** Đã xử lý xong, {@link #responseJson} có dữ liệu để replay. */
    public static final String COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false)
    private String scope;

    @Column(name = "idem_key", length = 191, nullable = false)
    private String idemKey;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    /** PROCESSING | COMPLETED */
    @Column(length = 16, nullable = false)
    private String status;

    @Lob
    @Column(name = "response_json", columnDefinition = "LONGTEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
