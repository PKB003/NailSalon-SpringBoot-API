package com.ttnails.booking_service.staff.repository;

import com.ttnails.booking_service.staff.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findByStaffIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
            Long staffId, LocalDate from, LocalDate to);

    List<Shift> findByStaffIdInAndWorkDate(Collection<Long> staffIds, LocalDate workDate);

    boolean existsByStaffIdAndWorkDateAndStartTimeLessThanAndEndTimeGreaterThan(
            Long staffId, LocalDate workDate, LocalTime endTime, LocalTime startTime);

    long countByStaffIdAndWorkDateBetween(Long staffId, LocalDate from, LocalDate to);
}
