package com.ttnails.booking_service.bookings.repository;

import com.ttnails.booking_service.bookings.entity.CancellationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CancellationRecordRepository extends JpaRepository<CancellationRecord, Long> {

    List<CancellationRecord> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
