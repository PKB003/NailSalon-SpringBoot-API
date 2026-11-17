package com.ttnails.booking_service.common.enums;

/**
 * Vai trò trong hệ thống (S1-07).
 *
 * <ul>
 *   <li>{@link #USER} — khách hàng, chỉ quản lý booking của mình.</li>
 *   <li>{@link #NAIL_TECH} — thợ, chỉ thấy lịch của chính mình ({@code /staff/me/**}).</li>
 *   <li>{@link #RECEPTIONIST} — lễ tân, đọc lịch + đổi trạng thái booking.</li>
 *   <li>{@link #MANAGER} — quản lý chi nhánh, quản trị thợ/ca/nghỉ phép.</li>
 *   <li>{@link #ADMIN} — toàn quyền.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    USER,
    NAIL_TECH,
    RECEPTIONIST,
    MANAGER
}
