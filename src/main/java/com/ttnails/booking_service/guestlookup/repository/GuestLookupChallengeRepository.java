package com.ttnails.booking_service.guestlookup.repository;

import com.ttnails.booking_service.guestlookup.entity.GuestLookupChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface GuestLookupChallengeRepository extends JpaRepository<GuestLookupChallenge, Long> {

    Optional<GuestLookupChallenge> findByChallengeId(String challengeId);

    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime since);

    long countByConfirmationCodeAndCreatedAtAfter(String confirmationCode, LocalDateTime since);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
