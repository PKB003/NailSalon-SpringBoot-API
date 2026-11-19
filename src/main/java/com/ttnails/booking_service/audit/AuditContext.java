package com.ttnails.booking_service.audit;

/**
 * Ngữ cảnh audit tạm thời cho request hiện tại (S2-06).
 *
 * <p>Service set lý do trước khi gọi method {@link Audited}; aspect đọc lại và ghi vào
 * audit_log, sau đó clear để tránh rò rỉ sang request khác (thread pool).</p>
 */
public final class AuditContext {

    private static final ThreadLocal<String> REASON = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void setReason(String reason) {
        if (reason == null || reason.isBlank()) {
            REASON.remove();
        } else {
            REASON.set(reason);
        }
    }

    public static String getReason() {
        return REASON.get();
    }

    public static void clear() {
        REASON.remove();
    }

    /** Chạy action với lý do audit, luôn clear sau khi xong. */
    public static <T> T withReason(String reason, java.util.function.Supplier<T> action) {
        setReason(reason);
        try {
            return action.get();
        } finally {
            clear();
        }
    }
}
