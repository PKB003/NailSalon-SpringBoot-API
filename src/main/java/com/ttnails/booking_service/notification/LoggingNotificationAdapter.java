package com.ttnails.booking_service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Adapter tạm thời cho Sprint 2: ghi log thay vì gửi SMS/email thật.
 *
 * <p>S5-01 sẽ thay bằng outbox + worker; giữ nguyên interface để service không phải sửa.</p>
 */
@Component
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void waitlistSlotFreed(String phone, String name, Long serviceId,
                                  LocalDate date, String detail) {
        log.info("[WAITLIST] slot freed -> phone={} name={} serviceId={} date={} detail={}",
                mask(phone), name, serviceId, date, detail);
    }

    @Override
    public void sendLookupOtp(String phone, String confirmationCode, String otp) {
        // Không log OTP ở mức INFO để tránh lộ mã trong log production.
        log.info("[LOOKUP-OTP] queued otp for phone={} confirmationCode={}", mask(phone), confirmationCode);
        log.debug("[LOOKUP-OTP] dev otp={} for code={}", otp, confirmationCode);
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }
}
