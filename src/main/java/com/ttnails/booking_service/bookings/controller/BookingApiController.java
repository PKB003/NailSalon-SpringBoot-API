package com.ttnails.booking_service.bookings.controller;

import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.bookings.dto.BookingStatusRequest;
import com.ttnails.booking_service.bookings.dto.NewBookingRequest;
import com.ttnails.booking_service.bookings.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Các endpoint theo API contract của frontend (base path /bookings).
 * Các endpoint cũ (/booking/...) vẫn giữ nguyên để tương thích ngược.
 */
@RestController
@RequestMapping("/bookings")
public class BookingApiController {
    @Autowired
    private BookingService bookingService;

    /** GET /bookings — danh sách toàn bộ booking (chỉ ADMIN) */
    @GetMapping
    public ApiResponse<List<BookingResponse>> getAllBookings() {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), bookingService.getAllBookingsResponse());
    }

    /** GET /bookings/availability?date=YYYY-MM-DD — các slot còn trống trong ngày */
    @GetMapping("/availability")
    public ApiResponse<Map<String, List<String>>> getAvailability(@RequestParam String date) {
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new AppException(ErrorCode.INVALID_DATE);
        }
        return new ApiResponse<>(200, "Success", LocalDateTime.now(),
                Map.of("slots", bookingService.getAvailability(parsed)));
    }

    /** GET /bookings/my — booking của khách đang đăng nhập */
    @GetMapping("/my")
    public ApiResponse<List<BookingResponse>> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), bookingService.getMyBookings(jwt));
    }

    /** POST /bookings — tạo booking (có token sẽ tự gắn user, không token vẫn đặt được như guest) */
    @PostMapping
    public ApiResponse<BookingResponse> createBooking(@RequestBody @Valid NewBookingRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(201, "Booking created", LocalDateTime.now(),
                bookingService.createBooking(request, jwt));
    }

    /** PATCH /bookings/:id/cancel — khách hủy lịch của mình (hoặc admin) */
    @PatchMapping("/{id}/cancel")
    public ApiResponse<BookingResponse> cancelBooking(@PathVariable Long id,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(200, "Booking cancelled", LocalDateTime.now(),
                bookingService.cancelBooking(id, jwt));
    }

    /** PATCH /bookings/:id/status — admin đổi trạng thái */
    @PatchMapping("/{id}/status")
    public ApiResponse<BookingResponse> updateStatus(@PathVariable Long id,
                                                     @RequestBody @Valid BookingStatusRequest request) {
        return new ApiResponse<>(200, "Status updated", LocalDateTime.now(),
                bookingService.updateStatus(id, request));
    }
}
