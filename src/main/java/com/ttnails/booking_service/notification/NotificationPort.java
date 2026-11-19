package com.ttnails.booking_service.notification;

/**
 * Cổng gửi thông báo (S2-04 waitlist, S2-08 OTP).
 *
 * <p>Sprint 2 chỉ cần hành vi "gửi được và không làm hỏng nghiệp vụ"; Sprint 5 sẽ thay
 * bằng outbox + worker + adapter email/SMS thật. Mọi implementation phải idempotent
 * theo (kênh, người nhận, sự kiện) để retry không gửi trùng.</p>
 */
public interface NotificationPort {

    /** Báo khách đang chờ rằng slot cho ngày/dịch vụ đã trống. */
    void waitlistSlotFreed(String phone, String name, Long serviceId,
                           java.time.LocalDate date, String detail);

    /** Gửi OTP tra cứu booking. */
    void sendLookupOtp(String phone, String confirmationCode, String otp);
}
