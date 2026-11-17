package com.ttnails.booking_service.staff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** Đăng ký nghỉ phép (S1-02). Khoảng [startAt, endAt). */
@Data
public class TimeOffRequest {

    @NotNull(message = "startAt is required")
    private LocalDateTime startAt;

    @NotNull(message = "endAt is required")
    private LocalDateTime endAt;

    @Size(max = 255)
    private String reason;
}
