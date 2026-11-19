package com.ttnails.booking_service.bookings.service;

import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.bookings.dto.GuestLookupChallengeResponse;
import com.ttnails.booking_service.bookings.dto.GuestLookupRequest;
import com.ttnails.booking_service.bookings.dto.GuestLookupResponse;
import com.ttnails.booking_service.bookings.dto.GuestLookupVerifyRequest;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.repository.BookingItemRepository;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.config.GuestLookupProperties;
import com.ttnails.booking_service.guestlookup.entity.GuestLookupChallenge;
import com.ttnails.booking_service.guestlookup.repository.GuestLookupChallengeRepository;
import com.ttnails.booking_service.notification.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Tra cứu booking của guest bằng confirmation code + SĐT (S2-08).
 *
 * <p>Luồng 2 bước:</p>
 * <ol>
 *   <li>{@code POST /bookings/lookup} — kiểm tra code + SĐT, tạo OTP gửi qua SMS.
 *       Rate limit theo SĐT để chống dò mã.</li>
 *   <li>{@code POST /bookings/lookup/verify} — xác thực OTP, trả booking tương ứng.</li>
 * </ol>
 *
 * <p>Chỉ lưu SHA-256 của OTP (salt = challengeId), OTP dùng 1 lần và hết hạn sau
 * {@code app.guest-lookup.otp-ttl-minutes}. Sai code/SĐT trả cùng một lỗi để không
 * tiết lộ booking của người khác.</p>
 */
@Service
public class GuestLookupService {

    private static final Logger log = LoggerFactory.getLogger(GuestLookupService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final GuestLookupChallengeRepository challengeRepository;
    private final GuestLookupProperties properties;
    private final NotificationPort notificationPort;
    private final BookingMapper bookingMapper;

    public GuestLookupService(BookingRepository bookingRepository,
                              BookingItemRepository bookingItemRepository,
                              GuestLookupChallengeRepository challengeRepository,
                              GuestLookupProperties properties,
                              NotificationPort notificationPort,
                              BookingMapper bookingMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.challengeRepository = challengeRepository;
        this.properties = properties;
        this.notificationPort = notificationPort;
        this.bookingMapper = bookingMapper;
    }

    @Transactional
    public GuestLookupChallengeResponse requestOtp(GuestLookupRequest request, String ip) {
        String code = normalizeCode(request.getConfirmationCode());
        String phone = ConfirmationCodeService.normalizePhone(request.getPhone());
        LocalDateTime now = LocalDateTime.now();

        long recent = challengeRepository.countByPhoneAndCreatedAtAfter(
                phone, now.minusMinutes(Math.max(properties.getRateWindowMinutes(), 1)));
        if (recent >= Math.max(properties.getMaxRequestsPerWindow(), 1)) {
            throw new AppException(ErrorCode.TOO_MANY_REQUESTS);
        }

        Booking booking = bookingRepository.findByConfirmationCodeIgnoreCase(code)
                .orElse(null);
        // Cùng một lỗi cho "mã không tồn tại" và "SĐT không khớp" để tránh dò dữ liệu.
        if (booking == null || !ConfirmationCodeService.phoneMatches(booking.getPhone(), request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_CONFIRMATION_CODE);
        }

        String challengeId = UUID.randomUUID().toString();
        String otp = generateOtp();
        LocalDateTime expiresAt = now.plusMinutes(Math.max(properties.getOtpTtlMinutes(), 1));

        GuestLookupChallenge challenge = GuestLookupChallenge.builder()
                .challengeId(challengeId)
                .confirmationCode(code)
                .phone(phone)
                .otpHash(hash(challengeId, otp))
                .attempts(0)
                .verified(false)
                .expiresAt(expiresAt)
                .ip(ip)
                .createdAt(now)
                .build();
        challengeRepository.save(challenge);

        notificationPort.sendLookupOtp(booking.getPhone(), code, otp);

        GuestLookupChallengeResponse response = new GuestLookupChallengeResponse();
        response.setChallengeId(challengeId);
        response.setExpiresAt(expiresAt);
        response.setAttemptsRemaining(properties.getMaxAttempts());
        response.setMessage("Falls die Angaben korrekt sind, erhalten Sie einen Code per SMS.");
        if (properties.isExposeOtp()) {
            response.setOtp(otp);
        }
        return response;
    }

    /**
     * Xác thực OTP.
     *
     * <p>{@code noRollbackFor = AppException.class} là bắt buộc: số lần nhập sai phải
     * được commit kể cả khi trả lỗi, nếu không transaction rollback sẽ reset bộ đếm
     * và kẻ dò mã có thể thử vô hạn.</p>
     */
    @Transactional(noRollbackFor = AppException.class)
    public GuestLookupResponse verifyOtp(GuestLookupVerifyRequest request) {
        GuestLookupChallenge challenge = challengeRepository.findByChallengeId(request.getChallengeId())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_OTP));

        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            challengeRepository.delete(challenge);
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }
        if (challenge.getAttempts() >= Math.max(properties.getMaxAttempts(), 1)) {
            throw new AppException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        }

        String expected = hash(challenge.getChallengeId(), request.getOtp().trim());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                challenge.getOtpHash().getBytes(StandardCharsets.UTF_8))) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            challengeRepository.save(challenge);
            if (challenge.getAttempts() >= properties.getMaxAttempts()) {
                throw new AppException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
            }
            throw new AppException(ErrorCode.INVALID_OTP);
        }

        Booking booking = bookingRepository.findByConfirmationCodeIgnoreCase(challenge.getConfirmationCode())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CONFIRMATION_CODE));
        if (!ConfirmationCodeService.phoneMatches(booking.getPhone(), challenge.getPhone())) {
            throw new AppException(ErrorCode.INVALID_CONFIRMATION_CODE);
        }

        // OTP dùng 1 lần.
        challenge.setVerified(true);
        challengeRepository.delete(challenge);

        BookingResponse bookingResponse = bookingMapper.toResponse(booking,
                bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
        GuestLookupResponse response = new GuestLookupResponse();
        response.setBooking(bookingResponse);
        response.setOtherBookings(List.of());
        return response;
    }

    /** Dọn challenge hết hạn (03:30 mỗi ngày). */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanupExpired() {
        long removed = challengeRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (removed > 0) {
            log.info("Cleaned up {} expired guest lookup challenges", removed);
        }
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String hash(String challengeId, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salt = challengeId + ":" + (otp != null ? otp : "");
            return HexFormat.of().formatHex(digest.digest(salt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
