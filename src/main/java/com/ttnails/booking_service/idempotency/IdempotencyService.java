package com.ttnails.booking_service.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.config.BookingPolicyProperties;
import com.ttnails.booking_service.idempotency.entity.IdempotencyRecord;
import com.ttnails.booking_service.idempotency.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Idempotency-Key cho POST /bookings (S2-07).
 *
 * <p>Cách hoạt động:</p>
 * <ol>
 *   <li>INSERT key với status PROCESSING trong transaction riêng (commit ngay) —
 *       unique index {@code (scope, idem_key)} là trọng tài giữa các request song song.</li>
 *   <li>Nếu insert trùng: đọc bản ghi cũ.
 *     <ul>
 *       <li>Hash request khác → 409 {@code IDEMPOTENCY_KEY_REUSED}.</li>
 *       <li>Đã COMPLETED → trả nguyên response JSON (replay, không tạo booking mới).</li>
 *       <li>Đang PROCESSING → 409 {@code IDEMPOTENCY_IN_PROGRESS} (client retry sau).</li>
 *     </ul>
 *   </li>
 *   <li>Chạy nghiệp vụ → lưu response JSON; nếu lỗi thì xoá key để client retry được.</li>
 * </ol>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    /** PROCESSING cũ hơn ngưỡng này coi như request trước đã chết → cho takeover. */
    private static final int STALE_PROCESSING_SECONDS = 60;

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final BookingPolicyProperties policyProperties;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              ObjectMapper objectMapper,
                              BookingPolicyProperties policyProperties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.policyProperties = policyProperties;
    }

    /**
     * Chạy {@code action} đúng một lần cho mỗi cặp (scope, key).
     *
     * @param key     giá trị header {@code Idempotency-Key}; null/blank = không idempotent
     * @param request body request dùng để tính hash
     */
    public <T> T execute(String scope, String key, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }
        String idemKey = key.trim();
        if (idemKey.length() > 190) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        String requestHash = sha256(toJson(request));

        // Đọc trước để retry thông thường không tạo INSERT trùng (tránh log lỗi unique).
        // Tính đúng đắn của trường hợp song song vẫn dựa vào unique index bên dưới.
        if (repository.findByScopeAndIdemKey(scope, idemKey).isPresent()) {
            java.util.Optional<T> replayed = replayOrReject(scope, idemKey, requestHash, responseType);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        } else if (!tryAcquire(scope, idemKey, requestHash)) {
            java.util.Optional<T> replayed = replayOrReject(scope, idemKey, requestHash, responseType);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }

        try {
            T result = action.get();
            complete(scope, idemKey, result);
            return result;
        } catch (RuntimeException ex) {
            // Nghiệp vụ thất bại → xoá key để client có thể retry với cùng key.
            release(scope, idemKey);
            throw ex;
        }
    }

    private boolean tryAcquire(String scope, String key, String requestHash) {
        LocalDateTime now = LocalDateTime.now();
        IdempotencyRecord record = IdempotencyRecord.builder()
                .scope(scope)
                .idemKey(key)
                .requestHash(requestHash)
                .status(IdempotencyRecord.PROCESSING)
                .createdAt(now)
                .expiresAt(now.plusHours(Math.max(policyProperties.getIdempotencyTtlHours(), 1)))
                .build();
        try {
            repository.saveAndFlush(record);
            return true;
        } catch (DataIntegrityViolationException | TransactionSystemException ex) {
            log.debug("Duplicate Idempotency-Key {} for scope {}", key, scope);
            return false;
        }
    }

    private <T> java.util.Optional<T> replayOrReject(String scope, String key, String requestHash,
                                                     Class<T> responseType) {
        IdempotencyRecord existing = repository.findByScopeAndIdemKey(scope, key)
                .orElseThrow(() -> new AppException(ErrorCode.IDEMPOTENCY_IN_PROGRESS));

        if (!existing.getRequestHash().equals(requestHash)) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (IdempotencyRecord.COMPLETED.equals(existing.getStatus()) && existing.getResponseJson() != null) {
            try {
                return java.util.Optional.ofNullable(
                        objectMapper.readValue(existing.getResponseJson(), responseType));
            } catch (Exception ex) {
                log.warn("Cannot deserialize idempotent response for {}: {}", key, ex.getMessage());
                throw new AppException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }
        }
        // Đang xử lý: nếu quá cũ thì takeover, ngược lại yêu cầu client chờ.
        if (existing.getCreatedAt() != null
                && existing.getCreatedAt().isBefore(LocalDateTime.now().minusSeconds(STALE_PROCESSING_SECONDS))) {
            existing.setCreatedAt(LocalDateTime.now());
            existing.setStatus(IdempotencyRecord.PROCESSING);
            existing.setResponseJson(null);
            repository.saveAndFlush(existing);
            return java.util.Optional.empty();
        }
        throw new AppException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
    }

    private void complete(String scope, String key, Object result) {
        repository.findByScopeAndIdemKey(scope, key).ifPresent(record -> {
            record.setStatus(IdempotencyRecord.COMPLETED);
            record.setResponseJson(toJson(result));
            repository.saveAndFlush(record);
        });
    }

    private void release(String scope, String key) {
        try {
            repository.findByScopeAndIdemKey(scope, key).ifPresent(repository::delete);
        } catch (RuntimeException ex) {
            log.warn("Cannot release idempotency key {}: {}", key, ex.getMessage());
        }
    }

    /** Dọn key hết hạn (chạy 03:15 mỗi ngày). */
    @Scheduled(cron = "0 15 3 * * *")
    public void cleanupExpired() {
        long removed = repository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (removed > 0) {
            log.info("Cleaned up {} expired idempotency keys", removed);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
