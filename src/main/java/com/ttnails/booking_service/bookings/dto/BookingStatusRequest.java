package com.ttnails.booking_service.bookings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BookingStatusRequest {
    @NotBlank(message = "Status is required")
    private String status;

    /** optional: gán/thay đổi nhân viên phụ trách */
    private String employee;
}
