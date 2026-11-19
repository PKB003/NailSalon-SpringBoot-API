package com.ttnails.booking_service.idempotency.repository;

import com.ttnails.booking_service.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByScopeAndIdemKey(String scope, String idemKey);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
