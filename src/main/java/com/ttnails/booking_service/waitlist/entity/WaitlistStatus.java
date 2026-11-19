package com.ttnails.booking_service.waitlist.entity;

/** Trạng thái entry waitlist (S2-04). */
public final class WaitlistStatus {
    /** Đang chờ slot. */
    public static final String ACTIVE = "ACTIVE";
    /** Đã được thông báo có slot trống. */
    public static final String NOTIFIED = "NOTIFIED";
    /** Khách tự huỷ đăng ký. */
    public static final String CANCELLED = "CANCELLED";
    /** Khách đã đặt được lịch. */
    public static final String FULFILLED = "FULFILLED";

    private WaitlistStatus() {
    }
}
