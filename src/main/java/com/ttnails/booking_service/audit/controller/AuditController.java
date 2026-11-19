package com.ttnails.booking_service.audit.controller;

import com.ttnails.booking_service.audit.AuditService;
import com.ttnails.booking_service.audit.dto.AuditLogDTO;
import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.common.dto.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Truy vấn audit log (S2-06) — chỉ ADMIN.
 *
 * <pre>
 * GET /audit?entityType=BOOKING&amp;entityId=42&amp;action=BOOKING_CANCEL
 *          &amp;actorId=&lt;uuid&gt;&amp;from=2026-11-01T00:00:00&amp;to=2026-11-30T23:59:59
 *          &amp;page=0&amp;size=20
 * </pre>
 */
@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<AuditLogDTO>> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<com.ttnails.booking_service.audit.entity.AuditLog> result =
                auditService.search(entityType, entityId, action, actorId, from, to, page, size);
        PageResponse<AuditLogDTO> mapped = PageResponse.<AuditLogDTO>builder()
                .content(result.getContent().stream().map(AuditLogDTO::from).toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
        return ApiResponse.success(200, "Success", mapped);
    }
}
