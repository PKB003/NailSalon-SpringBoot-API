package com.ttnails.booking_service.bookings.repository;

import com.ttnails.booking_service.bookings.entity.BookingItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    @EntityGraph(attributePaths = {"service", "staff"})
    List<BookingItem> findByBookingIdOrderByStartAtAsc(Long bookingId);

    @EntityGraph(attributePaths = {"service", "staff"})
    List<BookingItem> findByBookingIdInOrderByStartAtAsc(List<Long> bookingIds);
}
