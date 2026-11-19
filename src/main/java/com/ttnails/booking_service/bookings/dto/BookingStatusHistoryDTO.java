package com.ttnails.booking_service.bookings.dto;

import com.ttnails.booking_service.bookings.entity.BookingStatusHistory;
import lombok.Data;

import java.time.LocalDateTime;

/** Một dòng lịch sử trạng thái booking (S2-01). */
@Data
public class BookingStatusHistoryDTO {
    private Long id;
    private String fromStatus;
    private String toStatus;
    private String actorId;
    private String actorLabel;
    private String actorRole;
    private String reason;
    private LocalDateTime createdAt;

    public static BookingStatusHistoryDTO from(BookingStatusHistory history) {
        BookingStatusHistoryDTO dto = new BookingStatusHistoryDTO();
        dto.id = history.getId();
        dto.fromStatus = history.getFromStatus();
        dto.toStatus = history.getToStatus();
        dto.actorId = history.getActorId();
        dto.actorLabel = history.getActorLabel();
        dto.actorRole = history.getActorRole();
        dto.reason = history.getReason();
        dto.createdAt = history.getCreatedAt();
        return dto;
    }
}
