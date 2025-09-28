package com.ttnails.booking_service.dto;

import com.ttnails.booking_service.entity.Booking;
import com.ttnails.booking_service.entity.User;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class BookingRequest {
    @NotNull(message = "first name can't be empty")
    private String firstName;
    @NotNull(message = "last name can't be empty")
    private String lastName;
    @Email @NotNull
    private String email;
    @NotNull @Pattern(regexp = "^(0|\\+49)[0-9]{9,10}$", message = "Invalid phone number") @Size(min = 10, max = 11, message = "invalid phone number")
    private String phone;
    @NotNull(message = "Please choose a service")
    private String service;
    @Future(message = "Date must be in the future")
    private LocalDateTime preferredDateAndTime;
    private String note;
}
