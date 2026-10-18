package com.ttnails.booking_service.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProfileUpdateRequest {
    private String name;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^[0-9+()\\-\\s]{6,20}$", message = "Invalid phone number")
    private String phone;

    private LocalDate birthday;
}
