package com.ttnails.booking_service.bookings.service;

import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.entity.CancellationPolicy;
import com.ttnails.booking_service.bookings.repository.CancellationPolicyRepository;
import com.ttnails.booking_service.config.BookingPolicyProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resolve chính sách huỷ áp dụng cho một booking (S2-03).
 *
 * <p>Ưu tiên policy riêng của dịch vụ, sau đó policy mặc định của chi nhánh; nếu
 * DB chưa có policy nào thì fallback về {@link BookingPolicyProperties} để hệ thống
 * luôn chạy được (D4: 24h / WARN / phí 0).</p>
 */
@Service
public class CancellationPolicyService {

    private final CancellationPolicyRepository policyRepository;
    private final BookingPolicyProperties properties;

    public CancellationPolicyService(CancellationPolicyRepository policyRepository,
                                     BookingPolicyProperties properties) {
        this.policyRepository = policyRepository;
        this.properties = properties;
    }

    public ResolvedPolicy resolve(Booking booking) {
        Long branchId = booking.getStaff() != null && booking.getStaff().getBranch() != null
                ? booking.getStaff().getBranch().getId()
                : null;
        CancellationPolicy policy = null;
        if (branchId != null) {
            List<CancellationPolicy> policies =
                    policyRepository.findByBranchIdAndActiveTrueOrderByServiceIdDesc(branchId);
            policy = policies.stream()
                    .filter(p -> p.appliesTo(LocalDateTime.now()))
                    .filter(p -> p.getServiceId() != null
                            && p.getServiceId().equals(booking.getServiceId()))
                    .findFirst()
                    .orElseGet(() -> policies.stream()
                            .filter(p -> p.appliesTo(LocalDateTime.now()))
                            .filter(p -> p.getServiceId() == null)
                            .findFirst()
                            .orElse(null));
        }
        if (policy == null) {
            return new ResolvedPolicy(
                    Math.max(properties.getFreeCancelHours(), 0),
                    properties.getLateCancelAction(),
                    "PERCENT",
                    properties.getLateCancelFeePercent(),
                    BigDecimal.ZERO);
        }
        return new ResolvedPolicy(
                Math.max(policy.getFreeCancelHours(), 0),
                policy.getLateCancelAction(),
                policy.getLateCancelFeeType(),
                policy.getLateCancelFeeValue(),
                policy.getNoShowFeeValue());
    }

    /**
     * @param start  giờ hẹn
     * @param policy policy đã resolve
     * @param now    thời điểm huỷ
     * @return true nếu huỷ trong cửa sổ phí (trước giờ hẹn ít hơn freeCancelHours)
     */
    public boolean isLate(LocalDateTime start, ResolvedPolicy policy, LocalDateTime now) {
        if (start == null) return false;
        return start.isBefore(now.plusHours(policy.freeCancelHours()));
    }

    /**
     * Tính phí huỷ trễ.
     *
     * <p>MVP không cọc nên refund luôn = 0; phí chỉ mang tính ghi nhận cho tới khi
     * thanh toán (S3) được bật.</p>
     */
    public BigDecimal computeLateFee(BigDecimal bookingPrice, ResolvedPolicy policy, boolean late) {
        if (!late || bookingPrice == null) return BigDecimal.ZERO;
        if (!"FEE".equalsIgnoreCase(policy.lateCancelAction())) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = policy.feeValue() != null ? policy.feeValue() : BigDecimal.ZERO;
        if ("PERCENT".equalsIgnoreCase(policy.feeType())) {
            return bookingPrice.multiply(value)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @param freeCancelHours  số giờ huỷ miễn phí
     * @param lateCancelAction WARN | FEE | BLOCK
     * @param feeType          NONE | FIXED | PERCENT
     * @param feeValue         số tiền hoặc phần trăm
     * @param noShowFee        phí no-show (S3; MVP 0)
     */
    public record ResolvedPolicy(int freeCancelHours, String lateCancelAction, String feeType,
                                 BigDecimal feeValue, BigDecimal noShowFee) {

        public String snapshot() {
            return "freeCancelHours=" + freeCancelHours
                    + ", lateCancelAction=" + lateCancelAction
                    + ", feeType=" + feeType
                    + ", feeValue=" + feeValue
                    + ", noShowFee=" + noShowFee;
        }
    }
}
