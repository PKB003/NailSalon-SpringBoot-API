package com.ttnails.booking_service.bookings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Bước 1 tra cứu booking của guest: confirmation code + số điện thoại (S2-08). */
@Data
public class GuestLookupRequest {

    @NotBlank(message = "confirmationCode is required")
    @Size(max = 16)
    private String confirmationCode;

    @NotBlank(message = "phone is required")
    @Size(max = 64)
    private String phone;
}
