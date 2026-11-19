package com.ttnails.booking_service.common.constant;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trạng thái booking theo state machine chuẩn production (S2-01) + chuỗi tiếng Đức
 * tương thích contract frontend.
 *
 * <pre>
 * PENDING_PAYMENT ──pay──> CONFIRMED ──check-in──> CHECKED_IN ──start──> IN_SERVICE ──done──> COMPLETED
 *        │                    │                        │                     │
 *        │ timeout            │ cancel                 │ no-show             │ cancel (admin)
 *        ▼                    ▼                        ▼                     ▼
 *    EXPIRED              CANCELLED                 NO_SHOW              CANCELLED
 * </pre>
 *
 * <p>MVP không cọc (D4) nên booking mới luôn bắt đầu ở {@link #CONFIRMED};
 * {@link #PENDING_PAYMENT}/{@link #EXPIRED} để sẵn cho S3 khi bật deposit.</p>
 *
 * <p>{@link #COMPLETED}, {@link #CANCELLED}, {@link #NO_SHOW}, {@link #EXPIRED} là
 * <b>terminal</b> — bất biến kế toán, khóa mọi transition kể cả admin.</p>
 */
public final class BookingStatus {

    /** S3 (deposit): chờ thanh toán cọc. */
    public static final String PENDING_PAYMENT = "zahlung ausstehend";
    /** Booking đã xác nhận (mặc định của MVP — không cọc). */
    public static final String CONFIRMED = "bestätigt";
    /** Khách đã check-in tại tiệm. */
    public static final String CHECKED_IN = "eingecheckt";
    /** Đang thực hiện dịch vụ (tên cũ của S0/S1). */
    public static final String IN_SERVICE = "in Bearbeitung";
    /** @deprecated dùng {@link #IN_SERVICE} — giữ alias cho code S1. */
    @Deprecated
    public static final String IN_PROGRESS = IN_SERVICE;
    public static final String COMPLETED = "abgeschlossen";
    public static final String CANCELLED = "storniert";
    /** Khách không đến (S2-05). */
    public static final String NO_SHOW = "nicht erschienen";
    /** Hết hạn thanh toán cọc (S3). */
    public static final String EXPIRED = "abgelaufen";

    public static final Set<String> ALL = Set.of(
            PENDING_PAYMENT, CONFIRMED, CHECKED_IN, IN_SERVICE,
            COMPLETED, CANCELLED, NO_SHOW, EXPIRED);

    public static final Set<String> TERMINAL = Set.of(
            COMPLETED, CANCELLED, NO_SHOW, EXPIRED);

    /** Trạng thái chặn slot (mọi trạng thái không terminal). */
    public static final Set<String> BLOCKING = Set.of(
            PENDING_PAYMENT, CONFIRMED, CHECKED_IN, IN_SERVICE);

    /** Danh sách dùng cho câu query JPQL {@code NOT IN (:terminalStatuses)}. */
    public static final List<String> TERMINAL_LIST = List.of(
            COMPLETED, CANCELLED, NO_SHOW, EXPIRED);

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            PENDING_PAYMENT, Set.of(CONFIRMED, CANCELLED, EXPIRED),
            CONFIRMED, Set.of(CHECKED_IN, IN_SERVICE, COMPLETED, CANCELLED, NO_SHOW),
            CHECKED_IN, Set.of(IN_SERVICE, CANCELLED, NO_SHOW),
            IN_SERVICE, Set.of(CONFIRMED, COMPLETED, CANCELLED)
    );

    public static boolean isTerminal(String status) {
        return status != null && TERMINAL.contains(status);
    }

    public static boolean isKnown(String status) {
        return status != null && ALL.contains(status);
    }

    /**
     * Ma trận transition. Legacy booking chưa có status được coi là {@link #IN_SERVICE}
     * (dữ liệu S0). Cùng trạng thái = idempotent.
     */
    public static boolean isTransitionAllowed(String from, String to) {
        if (to == null) return false;
        String current = from != null ? from : IN_SERVICE;
        if (to.equals(current)) return true;
        if (isTerminal(current)) return false;
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(to);
    }

    private BookingStatus() {
    }
}
