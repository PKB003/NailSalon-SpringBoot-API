package com.ttnails.booking_service.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Ca làm trả về FE — giờ dạng "09:00" để hiển thị trực tiếp. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftDTO {
    private Long id;
    private Long staffId;
    private LocalDate workDate;
    private String startTime;
    private String endTime;
}
