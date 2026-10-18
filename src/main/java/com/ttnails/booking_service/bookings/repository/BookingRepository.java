package com.ttnails.booking_service.bookings.repository;

import com.ttnails.booking_service.bookings.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking,Long> {
    List<Booking> findByUserId(UUID id);
    // Đếm booking trong cửa sổ nửa mở [start, end) — dùng cho availability
    long countByPreferredDateTimeGreaterThanEqualAndPreferredDateTimeLessThan(LocalDateTime start, LocalDateTime end);

    // Khóa pessimistic các booking trong cửa sổ slot — chống race condition khi đặt lịch đồng thời
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.preferredDateTime >= :start AND b.preferredDateTime < :end")
    List<Booking> findLockedBySlotWindow(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Kiểm tra user còn booking hay không (chặn xóa user đang có lịch)
    boolean existsByUserId(UUID userId);
}
