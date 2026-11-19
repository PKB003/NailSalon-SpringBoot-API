package com.ttnails.booking_service.guestlookup.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Challenge OTP cho guest tra cứu booking (S2-08).
 *
 * <p>Chỉ lưu hash của OTP (SHA-256), không lưu plaintext. Mỗi challenge dùng 1 lần,
 * hết hạn sau vài phút và giới hạn số lần nhập sai.</p>
 */
@Entity
@Table(name = "guest_lookup_challenge",
        uniqueConstraints = @UniqueConstraint(name = "uk_glc_challenge", columnNames = "challenge_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestLookupChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", length = 36, nullable = false)
    private String challengeId;

    @Column(name = "confirmation_code", length = 16, nullable = false)
    private String confirmationCode;

    @Column(length = 64, nullable = false)
    private String phone;

    @Column(name = "otp_hash", length = 64, nullable = false)
    private String otpHash;

    @Column(nullable = false)
    private Integer attempts;

    @Column(nullable = false)
    private Boolean verified;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
