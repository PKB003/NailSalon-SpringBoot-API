package com.ttnails.booking_service.bookings.repository;

import com.ttnails.booking_service.bookings.entity.BookingVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingVersionRepository extends JpaRepository<BookingVersion, Long> {

    List<BookingVersion> findByBookingIdOrderByVersionNoAsc(Long bookingId);

    @Query("SELECT MAX(v.versionNo) FROM BookingVersion v WHERE v.booking.id = :bookingId")
    Optional<Integer> findMaxVersionNo(@Param("bookingId") Long bookingId);
}
