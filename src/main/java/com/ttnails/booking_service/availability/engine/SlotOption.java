package com.ttnails.booking_service.availability.engine;

import java.time.LocalDateTime;

/** Một slot khả dụng. {@code end} là giờ xong dịch vụ (không gồm buffer). */
public record SlotOption(LocalDateTime start, LocalDateTime end) {
}
