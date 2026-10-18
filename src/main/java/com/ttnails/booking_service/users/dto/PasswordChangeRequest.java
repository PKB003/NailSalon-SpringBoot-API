package com.ttnails.booking_service.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequest {
    @NotBlank(message = "Current password can't be blank")
    private String currentPassword;

    @NotBlank(message = "New password can't be blank")
    @Size(min = 6, message = "New password must be at least 6 characters")
    private String newPassword;
}
