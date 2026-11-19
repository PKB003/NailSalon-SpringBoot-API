package com.ttnails.booking_service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.audit.entity.AuditLog;
import com.ttnails.booking_service.audit.repository.AuditLogRepository;
import com.ttnails.booking_service.common.dto.PageResponse;
import com.ttnails.booking_service.common.web.RequestIdFilter;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ghi & truy vấn audit log (S2-06).
 *
 * <p>Audit ghi trong cùng transaction với nghiệp vụ: nếu nghiệp vụ rollback thì
 * audit cũng rollback (không log hành động chưa xảy ra).</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_JSON_LENGTH = 60_000;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(String action, String entityType, Object entityId,
                       Object before, Object after, String reason) {
        try {
            Actor actor = currentActor();
            AuditLog entry = AuditLog.builder()
                    .actorId(actor.id())
                    .actorEmail(actor.email())
                    .actorRole(actor.role())
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId != null ? String.valueOf(entityId) : null)
                    .beforeJson(toJson(before))
                    .afterJson(toJson(after))
                    .reason(reason != null && !reason.isBlank() ? reason : AuditContext.getReason())
                    .ip(currentIp())
                    .traceId(MDC.get(RequestIdFilter.MDC_KEY))
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            // Audit không được làm hỏng nghiệp vụ chính — log warning để điều tra sau.
            log.warn("Failed to write audit log for {} {}: {}", entityType, action, ex.getMessage());
        }
    }

    public void record(String action, String entityType, Object entityId, Object before, Object after) {
        record(action, entityType, entityId, before, after, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLog> search(String entityType, String entityId, String action,
                                         String actorId, LocalDateTime from, LocalDateTime to,
                                         int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actorId != null && !actorId.isBlank()) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLog> result = auditLogRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result);
    }

    private int clampSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, 100);
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String role = jwt.getClaimAsString("scope");
            return new Actor(
                    jwt.getClaimAsString("id"),
                    jwt.getSubject(),
                    role != null ? role.split(" ")[0] : null);
        }
        return new Actor(null, "system", "SYSTEM");
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String forwarded = attributes.getRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > MAX_JSON_LENGTH ? json.substring(0, MAX_JSON_LENGTH) + "…" : json;
        } catch (Exception ex) {
            return "\"<unserializable: " + value.getClass().getSimpleName() + ">\"";
        }
    }

    private record Actor(String id, String email, String role) {
    }
}
