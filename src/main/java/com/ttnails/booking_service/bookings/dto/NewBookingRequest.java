package com.ttnails.booking_service.bookings.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * POST /bookings.
 *
 * <p>S1-05: hỗ trợ đặt nhiều dịch vụ liên tiếp (tối đa 3) và chọn thợ cụ thể.
 * Field {@code serviceId} vẫn được giữ để tương thích frontend cũ; nếu cả
 * {@code serviceIds} và {@code serviceId} đều trống → lỗi 400 INVALID_REQUEST.</p>
 */
@Data
public class NewBookingRequest {

    /** Legacy: một dịch vụ duy nhất. */
    private Long serviceId;

    /** S1: 1..3 dịch vụ, giữ đúng thứ tự khách chọn (validate số lượng ở service để trả TOO_MANY_SERVICES). */
    private List<@NotNull Long> serviceIds;

    /** S1: thợ mong muốn; null = hệ thống tự chọn thợ ít việc nhất. */
    private Long staffId;

    @NotBlank(message = "Date is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date must have format yyyy-MM-dd")
    private String date;

    @NotBlank(message = "Time is required")
    @Pattern(regexp = "\\d{2}:\\d{2}", message = "Time must have format HH:mm")
    private String time;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String note;

    /**
     * S2-05: cho phép đặt lịch dù vượt ngưỡng no-show. Chỉ có hiệu lực với
     * staff (ADMIN/MANAGER/RECEPTIONIST); guest gửi cờ này sẽ bị bỏ qua.
     */
    private boolean overrideNoShow;
}
