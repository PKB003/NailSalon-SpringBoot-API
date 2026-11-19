package com.ttnails.booking_service.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu method cần ghi audit log (S2-06).
 *
 * <p>Aspect sẽ ghi actor (từ JWT), tham số (before) và kết quả (after) vào
 * {@code audit_log} sau khi method chạy thành công. Lý do có thể truyền qua
 * {@link AuditContext#withReason}.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Ví dụ {@code BOOKING_STATUS_UPDATE}. */
    String action();

    /** Ví dụ {@code BOOKING}, {@code STAFF}, {@code USER}. */
    String entityType();

    /** Ghi tham số request vào before_json. */
    boolean includeArgs() default true;

    /** Ghi giá trị trả về vào after_json. */
    boolean includeResult() default true;
}
