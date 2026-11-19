package com.ttnails.booking_service.bookings.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shape cho frontend (contract /bookings).
 *
 * <p>Giữ song song field tiếng Anh (client view) và tiếng Đức (admin view) để cả 2 view
 * dùng chung 1 shape. Từ S1 bổ sung thông tin thợ, giờ kết thúc và snapshot giá.</p>
 */
@Data
public class BookingResponse {
    private Long id;
    private Long serviceId;

    private String service;
    private String dienstleistung;

    private String date;
    private String datum;

    private String time;
    private String uhrzeit;

    /** Giờ kết thúc dịch vụ (không gồm buffer). */
    private String endDate;
    private String endTime;
    private String endUhrzeit;

    private String name;
    private String kunde;

    private String phone;
    private String email;
    private String note;

    /** Nhân viên dạng chuỗi — giữ để tương thích FE cũ. */
    private String employee;
    private String mitarbeiter;

    private Long staffId;
    private String staffName;

    private String status;
    private String price;
    private BigDecimal priceSnapshot;
    private String currency;

    /** Tổng thời lượng dịch vụ (phút), không gồm buffer. */
    private Integer totalDurationMinutes;

    // ==================== Sprint 2 ====================

    /** Mã tra cứu cho guest (S2-08). */
    private String confirmationCode;

    /** Số phiên bản lịch hiện tại (S2-02), bắt đầu từ 0. */
    private Integer versionNo;

    /** Thông tin huỷ gần nhất (S2-03) — null nếu booking chưa huỷ. */
    private CancellationInfo cancellation;

    /** Chi tiết từng dịch vụ — chỉ trả ở POST /bookings (tránh N+1 khi list). */
    private List<Item> items;

    @Data
    public static class CancellationInfo {
        private boolean late;
        private BigDecimal feeAmount;
        private BigDecimal refundAmount;
        private String reason;
        private String requestedByRole;
        private LocalDateTime cancelledAt;
    }

    @Data
    public static class Item {
        private Long serviceId;
        private String name;
        private String startTime;
        private String endTime;
        private Integer duration;
        private BigDecimal price;
        private String currency;
        private Long staffId;
        private String staffName;
    }
}
