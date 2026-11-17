package com.ttnails.booking_service.staff.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Nghỉ phép / vắng mặt (S1-01). Khoảng [startAt, endAt) bị loại khỏi availability. */
@Entity
@Table(name = "time_off")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TimeOffStatus status;
}
