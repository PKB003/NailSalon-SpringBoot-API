package com.ttnails.booking_service.bookings.service;

import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.bookings.dto.NewBookingRequest;
import com.ttnails.booking_service.idempotency.IdempotencyService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Bọc {@link BookingService#createBooking} bằng idempotency (S2-07).
 *
 * <p>Đặt ở tầng facade thay vì trong transaction nghiệp vụ để key được ghi nhận
 * (commit) trước khi tạo booking: hai request song song cùng key sẽ đụng unique
 * index và chỉ một request thực thi.</p>
 */
@Service
public class BookingFacade {

    private static final String SCOPE = "POST /bookings";

    private final BookingService bookingService;
    private final IdempotencyService idempotencyService;

    public BookingFacade(BookingService bookingService, IdempotencyService idempotencyService) {
        this.bookingService = bookingService;
        this.idempotencyService = idempotencyService;
    }

    public BookingResponse createBooking(NewBookingRequest request, Jwt jwt, String idempotencyKey) {
        return idempotencyService.execute(SCOPE, idempotencyKey, request, BookingResponse.class,
                () -> bookingService.createBooking(request, jwt));
    }
}
