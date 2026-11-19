package com.ttnails.booking_service.bookings.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Snapshot bất biến của booking trước mỗi lần đổi lịch (S2-02).
 *
 * <p>Đổi lịch không sửa đè dữ liệu cũ: mỗi lần đổi tạo 1 version mới chứa JSON của
 * lịch cũ, nhờ đó tra được lịch sử và đối soát khi có khiếu nại.</p>
 */
@Entity
@Table(name = "booking_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_bv_booking_version",
                columnNames = {"booking_id", "version_no"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Booking booking;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(length = 255)
    private String reason;

    @Lob
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
