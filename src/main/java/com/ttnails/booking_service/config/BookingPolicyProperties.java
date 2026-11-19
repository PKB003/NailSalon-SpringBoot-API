package com.ttnails.booking_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Chính sách booking cấu hình được (D4, S2-02/S2-03/S2-05) — đổi không cần deploy.
 *
 * <pre>
 * app:
 *   policy:
 *     free-cancel-hours: 24
 *     late-cancel-action: WARN        # WARN | FEE | BLOCK
 *     late-cancel-fee-percent: 0
 *     check-in-window-hours: 24
 *     idempotency-ttl-hours: 24
 *     no-show:
 *       max-count: 3
 *       window-days: 180
 *       enforcement: BLOCK            # WARN | BLOCK
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.policy")
@Data
public class BookingPolicyProperties {

    /** Số giờ trước giờ hẹn được coi là "huỷ miễn phí". */
    private int freeCancelHours = 24;

    /** WARN = vẫn cho huỷ + ghi late-cancel; FEE = tính phí; BLOCK = chặn khách tự huỷ. */
    private String lateCancelAction = "WARN";

    /** % giá trị booking thu khi late-cancel (chỉ dùng khi action = FEE). */
    private BigDecimal lateCancelFeePercent = BigDecimal.ZERO;

    /** Cho phép check-in sớm nhất bao nhiêu giờ trước giờ hẹn. */
    private int checkInWindowHours = 24;

    /** Thời gian sống của Idempotency-Key (giờ). */
    private int idempotencyTtlHours = 24;

    private NoShow noShow = new NoShow();

    @Data
    public static class NoShow {
        /** Số lần no-show tối đa trong cửa sổ trước khi áp dụng enforcement. */
        private int maxCount = 3;
        /** Cửa sổ đếm no-show (ngày). */
        private int windowDays = 180;
        /** WARN = chỉ cảnh báo; BLOCK = chặn đặt lịch (admin override được). */
        private String enforcement = "BLOCK";
    }
}
