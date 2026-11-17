package com.ttnails.booking_service.availability.engine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Kết quả availability — khớp contract 5.1 (Plan.md).
 *
 * @param totalDurationMinutes tổng thời lượng dịch vụ (không gồm buffer) để hiển thị
 * @param staff               chỉ gồm thợ có ít nhất 1 slot, sắp xếp theo staffId
 * @param anyAvailable        union giờ bắt đầu còn trống của mọi thợ (đã sắp xếp, không trùng)
 */
public record AvailabilityResult(LocalDate date,
                                 ZoneId zoneId,
                                 int slotStepMinutes,
                                 int totalDurationMinutes,
                                 List<StaffSlots> staff,
                                 List<LocalDateTime> anyAvailable) {

    public AvailabilityResult {
        staff = staff == null ? List.of() : List.copyOf(staff);
        anyAvailable = anyAvailable == null ? List.of() : List.copyOf(anyAvailable);
    }

    public boolean isEmpty() {
        return anyAvailable.isEmpty();
    }
}
