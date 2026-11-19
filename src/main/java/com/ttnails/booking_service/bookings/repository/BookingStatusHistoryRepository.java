package com.ttnails.booking_service.bookings.repository;

import com.ttnails.booking_service.bookings.entity.BookingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {

    List<BookingStatusHistory> findByBookingIdOrderByCreatedAtAscIdAsc(Long bookingId);
}
