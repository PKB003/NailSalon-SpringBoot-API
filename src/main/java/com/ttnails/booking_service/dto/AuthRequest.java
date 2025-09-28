package com.ttnails.booking_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthRequest {
    @Email
    private String email;
    @Pattern(regexp = "^(0|\\+49)[0-9]{9,10}$", message = "Invalid phone number") @Size(min = 10, max = 11, message = "invalid phone number")
    private String phone;
    @NotBlank(message = "Password can't be blank")
    private String password;

    public boolean isValid() {
        return (phone != null && !phone.isEmpty()) || (email != null && !email.isEmpty());
    }

}
