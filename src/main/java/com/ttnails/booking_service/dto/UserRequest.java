package com.ttnails.booking_service.dto;

import com.ttnails.booking_service.enums.Gender;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UserRequest {
    @NotNull
    private String name;
    @NotNull @NotNull @Pattern(regexp = "^(0|\\+49)[0-9]{9,10}$", message = "Invalid phone number") @Size(min = 10, max = 11, message = "invalid phone number")
    private String phone;
    private boolean isVerifiedPhone = false;
    @NotNull @Email(message = "Invalid email format")
    private String email;
    private boolean isVerifiedEmail = false;
    @NotNull
    private String password;
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> roles = new HashSet<>();
    @NotNull @Past
    private LocalDate dob;
    @NotNull
    private String address;
    @NotNull
    private Gender gender;
}
