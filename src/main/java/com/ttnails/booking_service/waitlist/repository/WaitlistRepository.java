package com.ttnails.booking_service.waitlist.repository;

import com.ttnails.booking_service.waitlist.entity.WaitlistEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByActiveKey(String activeKey);

    List<WaitlistEntry> findByServiceIdAndDesiredDateAndStatusOrderByCreatedAtAsc(
            Long serviceId, LocalDate desiredDate, String status);

    Page<WaitlistEntry> findByDesiredDateAndServiceIdAndStatus(
            LocalDate desiredDate, Long serviceId, String status, Pageable pageable);

    Page<WaitlistEntry> findByDesiredDateAndServiceId(
            LocalDate desiredDate, Long serviceId, Pageable pageable);

    long countByActiveKey(String activeKey);
}
