package com.ttnails.booking_service.bookings.dto;

import lombok.Data;

/** Đánh dấu khách không đến (S2-05). */
@Data
public class NoShowRequest {

    /** Lý do ghi vào history/audit. */
    private String reason;

    /**
     * Cho phép đánh dấu trước giờ hẹn (chỉ staff). Bắt buộc kèm {@link #reason}
     * để audit được ai override.
     */
    private boolean override;
}
