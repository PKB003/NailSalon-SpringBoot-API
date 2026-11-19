package com.ttnails.booking_service.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aspect ghi audit cho method gắn {@link Audited} (S2-06).
 *
 * <p>Tham số request → {@code before_json}; giá trị trả về → {@code after_json}.
 * Các tham số kỹ thuật (Jwt, HttpServletRequest…) bị bỏ qua.</p>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            try {
                Object before = audited.includeArgs() ? arguments(joinPoint) : null;
                Object after = audited.includeResult() ? result : null;
                auditService.record(audited.action(), audited.entityType(), entityId(joinPoint.getArgs(), result),
                        before, after, AuditContext.getReason());
            } catch (RuntimeException ex) {
                log.warn("Audit aspect failed for {}: {}", audited.action(), ex.getMessage());
            }
            return result;
        } finally {
            AuditContext.clear();
        }
    }

    private Map<String, Object> arguments(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null || arg instanceof Jwt) {
                continue;
            }
            String name = names != null && i < names.length ? names[i] : ("arg" + i);
            map.put(name, arg);
        }
        return map;
    }

    private Object entityId(Object[] args, Object result) {
        for (Object arg : args) {
            if (arg instanceof Long || arg instanceof Integer) {
                return arg;
            }
        }
        if (result != null) {
            try {
                return result.getClass().getMethod("getId").invoke(result);
            } catch (ReflectiveOperationException ignored) {
                // DTO không có getId → thử tham số string bên dưới
            }
        }
        for (Object arg : args) {
            if (arg instanceof String) {
                return arg;
            }
        }
        return null;
    }
}
