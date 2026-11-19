package com.ttnails.booking_service.bookings.repository;

import com.ttnails.booking_service.bookings.entity.CancellationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CancellationPolicyRepository extends JpaRepository<CancellationPolicy, Long> {

    /** Policy đang bật của chi nhánh, ưu tiên policy riêng cho service (service_id != null). */
    List<CancellationPolicy> findByBranchIdAndActiveTrueOrderByServiceIdDesc(Long branchId);
}
