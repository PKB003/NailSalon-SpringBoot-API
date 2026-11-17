package com.ttnails.booking_service.staff.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** Tạo ca làm (S1-02). Ví dụ: {"workDate":"2026-11-20","startTime":"09:00","endTime":"18:00"}. */
@Data
public class ShiftRequest {

    @NotNull(message = "workDate is required")
    private LocalDate workDate;

    @NotNull(message = "startTime is required")
    private LocalTime startTime;

    @NotNull(message = "endTime is required")
    private LocalTime endTime;
}
