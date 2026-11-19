package com.ttnails.booking_service.audit.dto;

import com.ttnails.booking_service.audit.entity.AuditLog;
import lombok.Data;

import java.time.LocalDateTime;

/** Bản ghi audit trả cho ADMIN qua {@code GET /audit}. */
@Data
public class AuditLogDTO {
    private Long id;
    private String actorId;
    private String actorEmail;
    private String actorRole;
    private String action;
    private String entityType;
    private String entityId;
    private String beforeJson;
    private String afterJson;
    private String reason;
    private String ip;
    private String traceId;
    private LocalDateTime createdAt;

    public static AuditLogDTO from(AuditLog log) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.id = log.getId();
        dto.actorId = log.getActorId();
        dto.actorEmail = log.getActorEmail();
        dto.actorRole = log.getActorRole();
        dto.action = log.getAction();
        dto.entityType = log.getEntityType();
        dto.entityId = log.getEntityId();
        dto.beforeJson = log.getBeforeJson();
        dto.afterJson = log.getAfterJson();
        dto.reason = log.getReason();
        dto.ip = log.getIp();
        dto.traceId = log.getTraceId();
        dto.createdAt = log.getCreatedAt();
        return dto;
    }
}
