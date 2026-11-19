package com.ttnails.booking_service.bookings.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bản ghi mỗi lần huỷ booking (S2-03): ai huỷ, có trễ không, phí/hoàn bao nhiêu,
 * policy tại thời điểm huỷ là gì.
 */
@Entity
@Table(name = "cancellation_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Booking booking;

    /** UUID người huỷ (null = guest dùng confirmation code). */
    @Column(name = "requested_by", length = 36)
    private String requestedBy;

    @Column(name = "requested_by_role", length = 32)
    private String requestedByRole;

    @Column(length = 500)
    private String reason;

    /** true = huỷ trong cửa sổ phí (trước giờ hẹn < free_cancel_hours). */
    @Column(nullable = false)
    private Boolean late;

    /** JSON policy áp dụng tại thời điểm huỷ. */
    @Column(name = "policy_snapshot", length = 1000)
    private String policySnapshot;

    @Column(name = "fee_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal feeAmount;

    @Column(name = "refund_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal refundAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
