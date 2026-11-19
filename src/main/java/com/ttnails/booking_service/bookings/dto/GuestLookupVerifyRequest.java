package com.ttnails.booking_service.bookings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Bước 2 tra cứu booking của guest: xác nhận OTP (S2-08). */
@Data
public class GuestLookupVerifyRequest {

    @NotBlank(message = "challengeId is required")
    private String challengeId;

    @NotBlank(message = "otp is required")
    private String otp;
}
