package com.ttnails.booking_service.bookings.service;

import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.config.BookingPolicyProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Quy tắc no-show (S2-05).
 *
 * <p>No-show không thu phí ở MVP (D4) nhưng được đếm trong cửa sổ cấu hình
 * ({@code app.policy.no-show.*}). Vượt ngưỡng → {@code BLOCK} hoặc chỉ {@code WARN}
 * tuỳ cấu hình; admin/manager có thể override kèm lý do (được audit).</p>
 */
@Service
public class NoShowPolicyService {

    private final BookingRepository bookingRepository;
    private final BookingPolicyProperties policy;

    public NoShowPolicyService(BookingRepository bookingRepository, BookingPolicyProperties policy) {
        this.bookingRepository = bookingRepository;
        this.policy = policy;
    }

    /** Số lần no-show của khách trong cửa sổ cấu hình. */
    @Transactional(readOnly = true)
    public int count(String phone, UUID userId) {
        LocalDateTime from = LocalDateTime.now().minusDays(Math.max(policy.getNoShow().getWindowDays(), 1));
        long byPhone = phone != null && !phone.isBlank()
                ? bookingRepository.countByPhoneAndStatusAndStartAtGreaterThanEqual(
                        phone, BookingStatus.NO_SHOW, from)
                : 0;
        long byUser = userId != null
                ? bookingRepository.countByUserIdAndStatusAndStartAtGreaterThanEqual(
                        userId, BookingStatus.NO_SHOW, from)
                : 0;
        return (int) Math.max(byPhone, byUser);
    }

    public boolean isBlocked(int count) {
        return "BLOCK".equalsIgnoreCase(policy.getNoShow().getEnforcement())
                && count >= policy.getNoShow().getMaxCount();
    }

    /** Chặn đặt lịch nếu vượt ngưỡng và không có override hợp lệ. */
    @Transactional(readOnly = true)
    public void assertAllowed(String phone, UUID userId, boolean override) {
        int count = count(phone, userId);
        if (!override && isBlocked(count)) {
            throw new AppException(ErrorCode.NO_SHOW_BLOCKED);
        }
    }

    public int maxCount() {
        return policy.getNoShow().getMaxCount();
    }

    public int windowDays() {
        return policy.getNoShow().getWindowDays();
    }

    public String enforcement() {
        return policy.getNoShow().getEnforcement();
    }
}
