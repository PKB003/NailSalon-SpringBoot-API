package com.ttnails.booking_service.services.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Service {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Thời lượng (phút) */
    @Builder.Default
    private Integer duration = 30;

    /** Giá (EUR) — S3 chuyển sang BigDecimal + snapshot đầy đủ hơn */
    @Builder.Default
    private Double price = 0.0;

    private String kategorie;

    private String beliebtheit;

    /** Buffer sau khi làm xong dịch vụ này (phút) — dùng cho AvailabilityEngine. */
    @Column(name = "buffer_minutes", nullable = false)
    @Builder.Default
    private Integer bufferMinutes = 10;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    /** Soft delete — không xoá cứng dịch vụ đã có booking (B-07, S2 sẽ siết thêm). */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    public int effectiveDuration() {
        return duration != null && duration > 0 ? duration : 30;
    }

    public int effectiveBuffer() {
        return bufferMinutes != null && bufferMinutes >= 0 ? bufferMinutes : 0;
    }
}
