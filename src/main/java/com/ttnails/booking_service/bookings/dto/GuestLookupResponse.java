package com.ttnails.booking_service.bookings.dto;

import lombok.Data;

import java.util.List;

/** Kết quả tra cứu sau khi xác thực OTP (S2-08). */
@Data
public class GuestLookupResponse {
    private BookingResponse booking;
    /** Các booking khác cùng số điện thoại (không bắt buộc, tiện cho khách). */
    private List<BookingResponse> otherBookings;
}
