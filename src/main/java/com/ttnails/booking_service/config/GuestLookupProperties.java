package com.ttnails.booking_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình tra cứu booking của guest (S2-08).
 *
 * <pre>
 * app:
 *   guest-lookup:
 *     otp-ttl-minutes: 10
 *     max-attempts: 5
 *     max-requests-per-window: 5
 *     rate-window-minutes: 15
 *     expose-otp: false      # chỉ bật ở dev/test để lấy OTP từ response
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.guest-lookup")
@Data
public class GuestLookupProperties {

    private int otpTtlMinutes = 10;
    private int maxAttempts = 5;
    private int maxRequestsPerWindow = 5;
    private int rateWindowMinutes = 15;

    /** Chỉ dùng cho dev/test: trả OTP trong response thay vì chỉ gửi qua SMS. */
    private boolean exposeOtp = false;
}
