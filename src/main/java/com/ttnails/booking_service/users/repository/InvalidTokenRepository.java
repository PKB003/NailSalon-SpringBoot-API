package com.ttnails.booking_service.users.repository;

import com.ttnails.booking_service.users.entity.InvalidToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public interface InvalidTokenRepository extends JpaRepository<InvalidToken,String> {
    /** Dọn token đã hết hạn (chạy định kỳ) — field "time" chính là expiryTime */
    long deleteByTimeBefore(Date time);
}
