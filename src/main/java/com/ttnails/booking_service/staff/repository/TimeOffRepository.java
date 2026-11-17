package com.ttnails.booking_service.staff.repository;

import com.ttnails.booking_service.staff.entity.TimeOff;
import com.ttnails.booking_service.staff.entity.TimeOffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TimeOffRepository extends JpaRepository<TimeOff, Long> {

    List<TimeOff> findByStaffIdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            Long staffId, TimeOffStatus status, LocalDateTime rangeEnd, LocalDateTime rangeStart);

    List<TimeOff> findByStaffIdInAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            Collection<Long> staffIds, TimeOffStatus status, LocalDateTime rangeEnd, LocalDateTime rangeStart);

    List<TimeOff> findByStaffIdOrderByStartAtAsc(Long staffId);

    boolean existsByStaffIdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            Long staffId, TimeOffStatus status, LocalDateTime rangeEnd, LocalDateTime rangeStart);
}
