package com.ttnails.booking_service.bookings.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Kết quả bước 1 tra cứu (S2-08).
 *
 * <p>{@code otp} chỉ có giá trị khi {@code app.guest-lookup.expose-otp=true}
 * (dev/test). Production luôn null — OTP được gửi qua SMS.</p>
 */
@Data
public class GuestLookupChallengeResponse {
    private String challengeId;
    private LocalDateTime expiresAt;
    private int attemptsRemaining;
    private String otp;
    private String message;
}
