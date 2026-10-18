package com.ttnails.booking_service.common.constant;

import java.util.Map;
import java.util.Set;

/**
 * Chuẩn hóa trạng thái booking theo API contract của frontend
 * + state machine chuẩn production cho salon.
 *
 * Ma trận transition hợp lệ:
 * - "in Bearbeitung" → bestätigt / abgeschlossen / storniert
 * - "bestätigt"      → in Bearbeitung (sửa nhầm) / abgeschlossen / storniert
 * - "abgeschlossen" / "storniert" là TERMINAL — khóa mọi transition (kể cả admin),
 *   để bảo vệ doanh thu, lịch sử khách hàng và tính toàn vẹn kế toán.
 */
public final class BookingStatus {
    public static final String CONFIRMED = "bestätigt";
    public static final String IN_PROGRESS = "in Bearbeitung";
    public static final String COMPLETED = "abgeschlossen";
    public static final String CANCELLED = "storniert";

    public static final Set<String> ALL = Set.of(CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED);

    public static final Set<String> TERMINAL = Set.of(COMPLETED, CANCELLED);

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            IN_PROGRESS, Set.of(CONFIRMED, COMPLETED, CANCELLED),
            CONFIRMED, Set.of(IN_PROGRESS, COMPLETED, CANCELLED)
    );

    public static boolean isTerminal(String status) {
        return TERMINAL.contains(status);
    }

    public static boolean isTransitionAllowed(String from, String to) {
        if (from == null) from = IN_PROGRESS; // dữ liệu legacy chưa có status
        if (to.equals(from)) return true; // idempotent
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    private BookingStatus() {
    }
}

