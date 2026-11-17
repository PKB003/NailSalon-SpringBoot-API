package com.ttnails.booking_service.availability.engine;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Availability Engine v2 (S1-03) — pure function, không truy cập DB.
 *
 * <p>Quy tắc:</p>
 * <ul>
 *   <li>Slot phải nằm trong <b>cả</b> ca làm và giờ mở cửa branch.</li>
 *   <li>Tổng thời gian cần = Σ duration + Σ buffer (buffer sau mỗi dịch vụ, kể cả dịch vụ cuối).</li>
 *   <li>Slot bị chặn nếu giao với nghỉ phép hoặc booking đang hoạt động
 *       {@code [startAt, bufferEndAt)}.</li>
 *   <li>Slot phải cách hiện tại ít nhất {@code minLeadMinutes} (cutoff đặt trước).</li>
 *   <li>Thợ phải có <b>đủ skill</b> cho mọi dịch vụ trong query.</li>
 * </ul>
 *
 * <p>Multi-service (D9) ở S1 dùng phương án A: <b>một thợ làm liên tiếp</b>.
 * Handoff nhiều thợ (phương án B) để Phase 3.</p>
 */
public class AvailabilityEngine {

    /** Sinh slot cho toàn bộ ngày. */
    public AvailabilityResult compute(AvailabilityQuery query) {
        List<ServiceSpec> services = query.services();
        int serviceDuration = services.stream().mapToInt(ServiceSpec::durationMinutes).sum();
        int requiredSpan = services.stream()
                .mapToInt(s -> s.durationMinutes() + s.bufferMinutes())
                .sum();

        LocalDateTime dayOpen = query.date().atTime(query.openTime());
        LocalDateTime dayClose = query.date().atTime(query.closeTime());
        LocalDateTime earliestStart = query.nowInZone().plusMinutes(query.minLeadMinutes());

        List<StaffSlots> staffSlots = new ArrayList<>();
        TreeSet<LocalDateTime> anyAvailable = new TreeSet<>();

        for (StaffSchedule staff : query.staff()) {
            if (query.requestedStaffId() != null && !query.requestedStaffId().equals(staff.staffId())) {
                continue;
            }
            if (!staff.hasSkillsFor(services)) {
                continue;
            }

            TreeSet<LocalDateTime> starts = new TreeSet<>();
            for (Interval shift : staff.shifts()) {
                LocalDateTime windowStart = max(shift.start(), dayOpen);
                LocalDateTime windowEnd = min(shift.end(), dayClose);
                if (!windowStart.isBefore(windowEnd)) {
                    continue;
                }

                LocalDateTime start = alignToGrid(dayOpen, windowStart, query.slotStepMinutes());
                while (!start.plusMinutes(requiredSpan).isAfter(windowEnd)) {
                    LocalDateTime busyEnd = start.plusMinutes(requiredSpan);
                    boolean free = !start.isBefore(earliestStart)
                            && !overlaps(staff.timeOff(), start, busyEnd)
                            && !overlaps(staff.busy(), start, busyEnd);
                    if (free) {
                        starts.add(start);
                    }
                    start = start.plusMinutes(query.slotStepMinutes());
                }
            }

            if (!starts.isEmpty()) {
                List<SlotOption> slots = starts.stream()
                        .map(s -> new SlotOption(s, s.plusMinutes(serviceDuration)))
                        .toList();
                staffSlots.add(new StaffSlots(staff.staffId(), staff.displayName(), slots));
                anyAvailable.addAll(starts);
            }
        }

        staffSlots.sort(Comparator.comparing(StaffSlots::staffId));
        return new AvailabilityResult(
                query.date(),
                query.zoneId(),
                query.slotStepMinutes(),
                serviceDuration,
                staffSlots,
                new ArrayList<>(anyAvailable));
    }

    /**
     * Căn slot lên lưới giờ mở cửa (ví dụ 09:00 + k*30') để mọi thợ trả cùng mốc giờ.
     */
    static LocalDateTime alignToGrid(LocalDateTime gridOrigin, LocalDateTime candidate, int stepMinutes) {
        if (candidate.isBefore(gridOrigin)) return gridOrigin;
        long minutes = Duration.between(gridOrigin, candidate).toMinutes();
        long steps = (minutes + stepMinutes - 1) / stepMinutes;
        return gridOrigin.plusMinutes(steps * stepMinutes);
    }

    private static boolean overlaps(List<Interval> intervals, LocalDateTime start, LocalDateTime end) {
        for (Interval interval : intervals) {
            if (interval.overlaps(start, end)) return true;
        }
        return false;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    /** Tiện cho test: lấy các slot (giờ bắt đầu) của một thợ. */
    public static List<LocalDateTime> startsOf(AvailabilityResult result, Long staffId) {
        return result.staff().stream()
                .filter(s -> s.staffId().equals(staffId))
                .findFirst()
                .map(s -> s.slots().stream().map(SlotOption::start).toList())
                .orElseGet(List::of);
    }

    /** Ngày mở cửa mặc định dùng khi test nhanh. */
    public static LocalDate dayOf(AvailabilityResult result) {
        return result.date();
    }
}
