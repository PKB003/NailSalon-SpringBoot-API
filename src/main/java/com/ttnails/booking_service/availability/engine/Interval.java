package com.ttnails.booking_service.availability.engine;

import java.time.LocalDateTime;

/**
 * Khoảng thời gian nửa mở [start, end).
 *
 * <p>Dùng cho ca làm, nghỉ phép và booking đang chặn lịch (booking dùng
 * {@code [startAt, bufferEndAt)} để buffer cũng chiếm chỗ).</p>
 */
public record Interval(LocalDateTime start, LocalDateTime end) {

    public Interval {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end are required");
        }
    }

    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start.isBefore(otherEnd) && end.isAfter(otherStart);
    }

    public long minutes() {
        return java.time.Duration.between(start, end).toMinutes();
    }
}
