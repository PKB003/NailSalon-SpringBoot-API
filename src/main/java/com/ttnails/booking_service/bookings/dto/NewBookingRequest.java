package com.ttnails.booking_service.bookings.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class NewBookingRequest {
    @NotNull(message = "Please choose a service")
    private Long serviceId;

    @NotBlank(message = "Date is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must have format yyyy-MM-dd")
    private String date;

    @NotBlank(message = "Time is required")
    @Pattern(regexp = "\\d{2}:\\d{2}", message = "Time must have format HH:mm")
    private String time;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String note;
}
