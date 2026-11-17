package com.ttnails.booking_service.availability.engine;

import java.util.List;
import java.util.Set;

/**
 * Dữ liệu của một thợ cho một ngày — AvailabilityEngine là pure function,
 * mọi truy vấn DB do {@code AvailabilityService} chuẩn bị trước.
 *
 * @param skillServiceIds các serviceId thợ làm được
 * @param shifts          ca làm đã giao với giờ mở cửa
 * @param timeOff         nghỉ phép ACTIVE trong ngày
 * @param busy            booking đang chặn dạng [startAt, bufferEndAt)
 */
public record StaffSchedule(Long staffId,
                            String displayName,
                            Set<Long> skillServiceIds,
                            List<Interval> shifts,
                            List<Interval> timeOff,
                            List<Interval> busy) {

    public StaffSchedule {
        skillServiceIds = skillServiceIds == null ? Set.of() : Set.copyOf(skillServiceIds);
        shifts = shifts == null ? List.of() : List.copyOf(shifts);
        timeOff = timeOff == null ? List.of() : List.copyOf(timeOff);
        busy = busy == null ? List.of() : List.copyOf(busy);
    }

    public boolean hasSkillsFor(List<ServiceSpec> services) {
        return services.stream().allMatch(s -> skillServiceIds.contains(s.serviceId()));
    }

    /** Tổng số phút đã bị booking chiếm trong ngày — dùng để chọn thợ ít việc nhất. */
    public long bookedMinutes() {
        return busy.stream().mapToLong(Interval::minutes).sum();
    }
}
