package com.ttnails.booking_service.waitlist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** POST /waitlist — đăng ký chờ slot (S2-04). */
@Data
public class WaitlistRequest {

    @NotNull(message = "serviceId is required")
    private Long serviceId;

    @NotBlank(message = "desiredDate is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "desiredDate must have format yyyy-MM-dd")
    private String desiredDate;

    /** Không bắt buộc — khách muốn quanh giờ nào (HH:mm). */
    @Pattern(regexp = "\\d{2}:\\d{2}", message = "desiredStartTime must have format HH:mm")
    private String desiredStartTime;

    @NotBlank(message = "phone is required")
    @Size(max = 64)
    private String phone;

    @Size(max = 255)
    private String name;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @Size(max = 500)
    private String note;
}
