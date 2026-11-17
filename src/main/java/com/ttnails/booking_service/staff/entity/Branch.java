package com.ttnails.booking_service.staff.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * Chi nhánh (D2: MVP 1 chi nhánh, schema sẵn cho multi-branch).
 * Giờ mở cửa, timezone và bước slot là cấu hình theo branch — AvailabilityEngine đọc từ đây.
 */
@Entity
@Table(name = "branch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** IANA timezone, ví dụ Europe/Berlin. Mọi tính toán "hôm nay" dùng zone này. */
    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "slot_step_minutes", nullable = false)
    private int slotStepMinutes;

    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes;

    private String phone;

    private String address;

    @Column(nullable = false)
    private boolean active;
}
