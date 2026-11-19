package com.ttnails.booking_service.bookings.entity;

import com.ttnails.booking_service.staff.entity.Staff;
import com.ttnails.booking_service.users.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "booking")
@Getter
@Setter
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String service;
    /** Khóa ngoại tới bảng services (theo API contract của frontend) */
    private Long serviceId;
    /** bestätigt | in Bearbeitung | abgeschlossen | storniert */
    private String status;
    /**
     * Tên nhân viên dạng chuỗi — chỉ còn dùng cho booking legacy (trước S1).
     *
     * @deprecated từ S1: dùng {@link #staff} + {@link #startAt}/{@link #endAt}.
     */
    @Deprecated
    private String employee;
    /** Giờ bắt đầu — giữ tên cũ để tương thích contract frontend. Trùng {@link #startAt}. */
    private LocalDateTime preferredDateTime;
    private String note;
    @ManyToOne()
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    // ==================== Sprint 1: staff + khoảng thời gian thực ====================

    /** Thợ phụ trách (null với booking legacy tạo trước S1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    /** end_at + buffer của dịch vụ cuối — dùng để chặn slot sát giờ. */
    @Column(name = "buffer_end_at")
    private LocalDateTime bufferEndAt;

    /** Snapshot tổng giá dịch vụ tại thời điểm đặt (tip/VAT sẽ thêm ở S3). */
    @Column(name = "price_snapshot", precision = 10, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(length = 3)
    private String currency;

    // ==================== Sprint 2: booking v2 ====================

    /** Mã xác nhận cho khách tra cứu (S2-08), ví dụ {@code TT-7K3M9Q}. Unique. */
    @Column(name = "confirmation_code", length = 16, unique = true)
    private String confirmationCode;

    public Booking() {
    }

    public Booking(String firstName, String lastName, String email, String phone, String service, LocalDateTime preferredDateTime, String note){
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.service = service;
        this.preferredDateTime = preferredDateTime;
        this.note = note;

    }

    /** Giờ kết thúc dịch vụ (không gồm buffer). Fallback cho booking legacy. */
    public LocalDateTime resolveEndAt() {
        return endAt != null ? endAt : preferredDateTime;
    }

    public LocalDateTime resolveBufferEndAt() {
        if (bufferEndAt != null) return bufferEndAt;
        return resolveEndAt();
    }
}
