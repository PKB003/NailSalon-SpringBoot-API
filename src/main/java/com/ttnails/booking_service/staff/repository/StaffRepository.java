package com.ttnails.booking_service.staff.repository;

import com.ttnails.booking_service.staff.entity.Staff;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    @EntityGraph(attributePaths = {"user"})
    List<Staff> findByBranchIdAndActiveTrueOrderByIdAsc(Long branchId);

    @EntityGraph(attributePaths = {"user"})
    List<Staff> findByActiveTrueOrderByIdAsc();

    Optional<Staff> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    long countByBranchIdAndActiveTrue(Long branchId);

    /**
     * Khóa staff row theo thứ tự id (S1-05) trước khi check overlap + insert.
     * Mọi transaction phải lock theo id ASC để tránh deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Staff s WHERE s.id = :id")
    Optional<Staff> findByIdForUpdate(@Param("id") Long id);
}
