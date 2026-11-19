package com.ttnails.booking_service.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Audit log append-only cho hành động admin/manager (S2-06).
 *
 * <p>Ghi ai làm gì, trên entity nào, trước/sau ra sao, lý do (nếu có), IP và traceId.
 * Không có API sửa/xoá — chỉ đọc qua {@code GET /audit} (ADMIN).</p>
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "actor_role", length = 32)
    private String actorRole;

    @Column(length = 64, nullable = false)
    private String action;

    @Column(name = "entity_type", length = 64, nullable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Lob
    @Column(name = "before_json", columnDefinition = "LONGTEXT")
    private String beforeJson;

    @Lob
    @Column(name = "after_json", columnDefinition = "LONGTEXT")
    private String afterJson;

    @Column(length = 500)
    private String reason;

    @Column(length = 64)
    private String ip;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
