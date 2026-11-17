package com.ttnails.booking_service.staff.dto;

import com.ttnails.booking_service.staff.entity.TimeOffStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Nghỉ phép trả về FE. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeOffDTO {
    private Long id;
    private Long staffId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String reason;
    private TimeOffStatus status;
}
