package com.ttnails.booking_service.staff.repository;

import com.ttnails.booking_service.staff.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByActiveTrueOrderByIdAsc();

    Optional<Branch> findFirstByActiveTrueOrderByIdAsc();
}
