package com.ttnails.booking_service.users.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "Name can't be blank")
    private String name;

    @NotBlank(message = "Email can't be blank")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone can't be blank")
    @Pattern(regexp = "^[0-9+()\\-\\s]{6,20}$", message = "Invalid phone number")
    private String phone;

    @NotBlank(message = "Password can't be blank")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private LocalDate birthday;
}
