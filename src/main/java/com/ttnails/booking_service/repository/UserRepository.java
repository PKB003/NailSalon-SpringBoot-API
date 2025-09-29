package com.ttnails.booking_service.repository;

import com.ttnails.booking_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    Optional<User> findByRolesContaining(String role);
    boolean existsByEmailAndIdNot(String email, UUID id);
    boolean existsByPhoneAndIdNot(String phone, UUID id);
}
