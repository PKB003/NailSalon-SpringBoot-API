package com.ttnails.booking_service.bookings.controller;

import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.bookings.dto.BookingStatusHistoryDTO;
import com.ttnails.booking_service.bookings.dto.BookingStatusRequest;
import com.ttnails.booking_service.bookings.dto.GuestLookupChallengeResponse;
import com.ttnails.booking_service.bookings.dto.GuestLookupRequest;
import com.ttnails.booking_service.bookings.dto.GuestLookupResponse;
import com.ttnails.booking_service.bookings.dto.GuestLookupVerifyRequest;
import com.ttnails.booking_service.bookings.dto.NewBookingRequest;
import com.ttnails.booking_service.bookings.dto.NoShowRequest;
import com.ttnails.booking_service.bookings.dto.RescheduleRequest;
import com.ttnails.booking_service.bookings.service.BookingFacade;
import com.ttnails.booking_service.bookings.service.BookingService;
import com.ttnails.booking_service.bookings.service.GuestLookupService;
import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.common.dto.PageResponse;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

    private final BookingService bookingService;
    private final BookingFacade bookingFacade;
    private final GuestLookupService guestLookupService;

    public BookingApiController(BookingService bookingService,
                                BookingFacade bookingFacade,
                                GuestLookupService guestLookupService) {
        this.bookingService = bookingService;
        this.bookingFacade = bookingFacade;
        this.guestLookupService = guestLookupService;
    }

    /**
     * GET /bookings — danh sách booking.
     *
     * <p>Không có {@code page} → giữ shape array cũ. Có {@code page} (hoặc filter)
     * → trả {@link PageResponse} (S2-09).</p>
     */
    @GetMapping
    public ApiResponse<?> getAllBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        if (page == null) {
            return new ApiResponse<>(200, "Success", LocalDateTime.now(),
                    bookingService.getAllBookingsResponse());
        }
        PageResponse<BookingResponse> result = bookingService.searchBookings(
                status, staffId, serviceId, from, to, query, page, size, sort);
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), result);
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

    /**
     * POST /bookings — tạo booking (có token sẽ tự gắn user, không token vẫn đặt được như guest).
     *
     * <p>S2-07: nếu client gửi {@code Idempotency-Key}, retry cùng key trả lại đúng
     * booking cũ thay vì tạo mới.</p>
     */
    @PostMapping
    public ApiResponse<BookingResponse> createBooking(
            @RequestBody @Valid NewBookingRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return new ApiResponse<>(201, "Booking created", LocalDateTime.now(),
                bookingFacade.createBooking(request, jwt, idempotencyKey));
    }

    /** PATCH /bookings/:id/cancel — khách hủy lịch của mình (hoặc admin) */
    @PatchMapping("/{id}/cancel")
    public ApiResponse<BookingResponse> cancelBooking(@PathVariable Long id,
                                                      @RequestParam(required = false) String reason,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(200, "Booking cancelled", LocalDateTime.now(),
                bookingService.cancelBooking(id, jwt, reason));
    }

    /** PATCH /bookings/:id/status — admin đổi trạng thái */
    @PatchMapping("/{id}/status")
    public ApiResponse<BookingResponse> updateStatus(@PathVariable Long id,
                                                     @RequestBody @Valid BookingStatusRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(200, "Status updated", LocalDateTime.now(),
                bookingService.updateStatus(id, request, jwt));
    }

    /** PATCH /bookings/:id/no-show — đánh dấu khách không đến (S2-05). */
    @PatchMapping("/{id}/no-show")
    public ApiResponse<BookingResponse> markNoShow(@PathVariable Long id,
                                                   @RequestBody(required = false) NoShowRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(200, "No-show marked", LocalDateTime.now(),
                bookingService.markNoShow(id, request != null ? request : new NoShowRequest(), jwt));
    }

    /** PATCH /bookings/:id/reschedule — đổi lịch (S2-02). */
    @PatchMapping("/{id}/reschedule")
    public ApiResponse<BookingResponse> reschedule(@PathVariable Long id,
                                                   @RequestBody @Valid RescheduleRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(200, "Booking rescheduled", LocalDateTime.now(),
                bookingService.rescheduleBooking(id, request, jwt));
    }

    /** GET /bookings/:id/history — lịch sử trạng thái (S2-01). */
    @GetMapping("/{id}/history")
    public ApiResponse<List<BookingStatusHistoryDTO>> history(@PathVariable Long id,
                                                              @AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(),
                bookingService.getHistory(id, jwt));
    }

    /**
     * POST /bookings/lookup — bước 1 tra cứu của guest: confirmation code + SĐT (S2-08).
     * Public nhưng rate-limited và không tiết lộ dữ liệu của người khác.
     */
    @PostMapping("/lookup")
    public ApiResponse<GuestLookupChallengeResponse> lookup(
            @RequestBody @Valid GuestLookupRequest request,
            HttpServletRequest httpRequest) {
        return new ApiResponse<>(200, "OTP sent", LocalDateTime.now(),
                guestLookupService.requestOtp(request, clientIp(httpRequest)));
    }

    /** POST /bookings/lookup/verify — bước 2: xác thực OTP và trả booking (S2-08). */
    @PostMapping("/lookup/verify")
    public ApiResponse<GuestLookupResponse> lookupVerify(
            @RequestBody @Valid GuestLookupVerifyRequest request) {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(),
                guestLookupService.verifyOtp(request));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
