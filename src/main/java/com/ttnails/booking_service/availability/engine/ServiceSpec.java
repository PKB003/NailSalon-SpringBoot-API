package com.ttnails.booking_service.availability.engine;

import java.math.BigDecimal;

/**
 * Một dịch vụ được đặt trong availability query (đã load từ catalog).
 *
 * @param durationMinutes thời lượng làm dịch vụ
 * @param bufferMinutes   buffer sau khi xong dịch vụ (phút)
 */
public record ServiceSpec(Long serviceId, String name, int durationMinutes, int bufferMinutes,
                          BigDecimal price) {

    public ServiceSpec {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes must be > 0");
        }
        if (bufferMinutes < 0) {
            throw new IllegalArgumentException("bufferMinutes must be >= 0");
        }
    }
}
