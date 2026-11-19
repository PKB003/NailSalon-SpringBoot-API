package com.ttnails.booking_service.bookings.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lịch sử trạng thái booking (S2-01) — append-only, mỗi transition 1 dòng.
 *
 * <p>Dùng để trả lời "ai đổi trạng thái, khi nào, vì sao" mà không phụ thuộc audit_log
 * (audit_log dành cho hành động admin nói chung).</p>
 */
@Entity
@Table(name = "booking_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Booking booking;

    /** null khi là transition đầu tiên (tạo booking). */
    @Column(name = "from_status", length = 64)
    private String fromStatus;

    @Column(name = "to_status", length = 64, nullable = false)
    private String toStatus;

    /** UUID user thực hiện (null = guest/hệ thống). */
    @Column(name = "actor_id", length = 36)
    private String actorId;

    /** Nhãn hiển thị: email hoặc "system". */
    @Column(name = "actor_label", length = 255)
    private String actorLabel;

    @Column(name = "actor_role", length = 32)
    private String actorRole;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
