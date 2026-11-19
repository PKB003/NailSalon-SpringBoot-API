package com.ttnails.booking_service.waitlist.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Đăng ký chờ slot (S2-04).
 *
 * <p>{@code activeKey} là khóa duy nhất chỉ tồn tại khi entry đang ACTIVE
 * ({@code phone|serviceId|yyyy-MM-dd}); khi NOTIFIED/CANCELLED thì set null để
 * khách có thể đăng ký lại và để chống spam trên mỗi ngày/dịch vụ.</p>
 */
@Entity
@Table(name = "waitlist",
        uniqueConstraints = @UniqueConstraint(name = "uk_wl_active", columnNames = "active_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "desired_date", nullable = false)
    private LocalDate desiredDate;

    @Column(name = "desired_start_time")
    private LocalTime desiredStartTime;

    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(length = 255)
    private String name;

    @Column(length = 64, nullable = false)
    private String phone;

    @Column(length = 255)
    private String email;

    /** ACTIVE | NOTIFIED | CANCELLED | FULFILLED */
    @Column(length = 16, nullable = false)
    private String status;

    @Column(name = "active_key", length = 191)
    private String activeKey;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
