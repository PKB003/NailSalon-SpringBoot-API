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
 * Chính sách huỷ theo chi nhánh/dịch vụ (D4, S2-03).
 *
 * <p>MVP seed 1 policy mặc định: {@code free_cancel_hours=24},
 * {@code late_cancel_action=WARN}, phí = 0. Chủ tiệm có thể đổi sang
 * {@code FEE}/{@code BLOCK} bằng dữ liệu, không cần deploy.</p>
 */
@Entity
@Table(name = "cancellation_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id")
    private Long branchId;

    /** null = policy mặc định của chi nhánh. */
    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "free_cancel_hours", nullable = false)
    private Integer freeCancelHours;

    /** WARN | FEE | BLOCK */
    @Column(name = "late_cancel_action", length = 16, nullable = false)
    private String lateCancelAction;

    /** NONE | FIXED | PERCENT */
    @Column(name = "late_cancel_fee_type", length = 16, nullable = false)
    private String lateCancelFeeType;

    @Column(name = "late_cancel_fee_value", precision = 10, scale = 2, nullable = false)
    private BigDecimal lateCancelFeeValue;

    @Column(name = "no_show_fee_type", length = 16, nullable = false)
    private String noShowFeeType;

    @Column(name = "no_show_fee_value", precision = 10, scale = 2, nullable = false)
    private BigDecimal noShowFeeValue;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    public boolean appliesTo(LocalDateTime at) {
        LocalDateTime moment = at != null ? at : LocalDateTime.now();
        if (validFrom != null && moment.isBefore(validFrom)) return false;
        return validTo == null || !moment.isAfter(validTo);
    }
}
