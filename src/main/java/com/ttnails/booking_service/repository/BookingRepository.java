package com.ttnails.booking_service.repository;

import com.ttnails.booking_service.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking,Long> {
    List<Booking> findByUserId(UUID id);
    long countByPreferredDateTimeIsBetween(LocalDateTime start, LocalDateTime end);
    long countByServiceAndPreferredDateTimeIsBetween(String service, LocalDateTime start, LocalDateTime end);
    long countByServiceAndPreferredDateTimeIsBetweenAndIdNot(String service, LocalDateTime slotStart,LocalDateTime slotEnd, Long excludeBookingId);
}
