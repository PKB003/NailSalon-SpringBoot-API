package com.ttnails.booking_service.bookings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * Đổi lịch booking (S2-02).
 *
 * <p>Khách chỉ được đổi trước cutoff ({@code free-cancel-hours}, mặc định 24h).
 * Admin/manager/receptionist có thể override nhưng phải gửi {@code reason} để audit.</p>
 */
@Data
public class RescheduleRequest {

    @NotBlank(message = "Date is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must have format yyyy-MM-dd")
    private String date;

    @NotBlank(message = "Time is required")
    @Pattern(regexp = "\\d{2}:\\d{2}", message = "Time must have format HH:mm")
    private String time;

    /** null = giữ nguyên các dịch vụ hiện tại. */
    private List<Long> serviceIds;

    /** null = giữ nguyên thợ hiện tại (nếu còn trống). */
    private Long staffId;

    /** Bắt buộc khi staff override cutoff. */
    private String reason;

    private String note;
}
