package com.ttnails.booking_service.users.service;

import com.ttnails.booking_service.users.repository.InvalidTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Dọn dẹp định kỳ token đã hết hạn trong bảng invalid_token.
 * Logout/refresh lưu token vào blacklist vĩnh viễn — không dọn thì bảng phình vô hạn.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvalidTokenCleanupService {
    private final InvalidTokenRepository invalidTokenRepository;

    /** Chạy 03:00 mỗi ngày */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void removeExpiredTokens() {
        long removed = invalidTokenRepository.deleteByTimeBefore(new Date());
        if (removed > 0) {
            log.info("Cleaned up {} expired invalid tokens", removed);
        }
    }
}