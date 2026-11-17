package com.ttnails.booking_service.availability.engine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Input đầy đủ cho AvailabilityEngine (S1-03). Không phụ thuộc Spring/JPA để test thuần.
 *
 * @param nowInZone        "bây giờ" theo timezone của branch
 * @param minLeadMinutes   phải đặt trước ít nhất bao nhiêu phút
 * @param slotStepMinutes  bước sinh slot (15/30)
 * @param openTime/closeTime giờ mở cửa branch
 */
public record AvailabilityQuery(LocalDate date,
                                ZoneId zoneId,
                                LocalDateTime nowInZone,
                                LocalTime openTime,
                                LocalTime closeTime,
                                int slotStepMinutes,
                                int minLeadMinutes,
                                List<ServiceSpec> services,
                                List<StaffSchedule> staff,
                                Long requestedStaffId) {

    public AvailabilityQuery {
        if (date == null) throw new IllegalArgumentException("date is required");
        if (zoneId == null) throw new IllegalArgumentException("zoneId is required");
        if (services == null || services.isEmpty()) {
            throw new IllegalArgumentException("at least one service is required");
        }
        if (slotStepMinutes <= 0) throw new IllegalArgumentException("slotStepMinutes must be > 0");
        services = List.copyOf(services);
        staff = staff == null ? List.of() : List.copyOf(staff);
    }
}
