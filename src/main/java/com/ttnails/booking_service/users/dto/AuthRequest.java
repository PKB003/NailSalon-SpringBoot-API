package com.ttnails.booking_service.users.dto;

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

    /** Frontend gửi 1 trường duy nhất: email hoặc số điện thoại */
    private String emailOrPhone;

    public AuthRequest() {
    }

    public AuthRequest(String email, String phone, String password) {
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    public boolean isValid() {
        boolean hasEmailOrPhone = emailOrPhone != null && !emailOrPhone.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        return hasEmailOrPhone || hasEmail || hasPhone;
    }

}
