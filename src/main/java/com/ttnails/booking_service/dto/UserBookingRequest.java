package com.ttnails.booking_service.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserBookingRequest {
    @NotNull(message = "Please choose a service")
    private String service;

    @Future(message = "Date must be in the future")
    @NotNull(message = "Preferred date and time is required")
    private LocalDateTime preferredDateAndTime;

    private String note;
}
