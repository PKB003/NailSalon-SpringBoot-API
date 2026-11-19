package com.ttnails.booking_service.bookings.repository;

import com.ttnails.booking_service.bookings.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    /** S2-09: danh sách admin có filter + phân trang (staff được fetch để mapper không N+1). */
    @Override
    @EntityGraph(attributePaths = {"staff"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    Optional<Booking> findByConfirmationCodeIgnoreCase(String confirmationCode);

    @EntityGraph(attributePaths = {"staff"})
    List<Booking> findByPhoneOrderByStartAtDesc(String phone);

    long countByPhoneAndStatusAndStartAtGreaterThanEqual(String phone, String status, LocalDateTime from);

    long countByUserIdAndStatusAndStartAtGreaterThanEqual(UUID userId, String status, LocalDateTime from);

    @EntityGraph(attributePaths = {"staff"})
    List<Booking> findByUserId(UUID id);

    @EntityGraph(attributePaths = {"staff"})
    List<Booking> findAllByOrderByPreferredDateTimeDesc();

    // Đếm booking trong cửa sổ nửa mở [start, end) — chỉ còn dùng cho booking legacy
    long countByPreferredDateTimeGreaterThanEqualAndPreferredDateTimeLessThan(LocalDateTime start, LocalDateTime end);

    // Khóa pessimistic các booking trong cửa sổ slot — legacy path
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.preferredDateTime >= :start AND b.preferredDateTime < :end")
    List<Booking> findLockedBySlotWindow(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Kiểm tra user còn booking hay không (chặn xóa user đang có lịch)
    boolean existsByUserId(UUID userId);

    /**
     * Các booking đang chặn lịch của danh sách thợ (S1-04/S1-05).
     *
     * <p>Overlap theo nửa mở: {@code existing.startAt < rangeEnd AND existing.bufferEndAt > rangeStart}.
     * Booking terminal (storniert/abgeschlossen) không chặn slot. Booking legacy không có
     * {@code startAt} cũng bị bỏ qua vì NULL không thoả điều kiện.</p>
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.staff.id IN :staffIds
              AND b.id <> :excludeBookingId
              AND b.startAt IS NOT NULL
              AND b.bufferEndAt IS NOT NULL
              AND b.status NOT IN :terminalStatuses
              AND b.startAt < :rangeEnd
              AND b.bufferEndAt > :rangeStart
            """)
    List<Booking> findBlockingForStaff(@Param("staffIds") Collection<Long> staffIds,
                                       @Param("rangeStart") LocalDateTime rangeStart,
                                       @Param("rangeEnd") LocalDateTime rangeEnd,
                                       @Param("terminalStatuses") Collection<String> terminalStatuses,
                                       @Param("excludeBookingId") Long excludeBookingId);

    /**
     * Kiểm tra overlap sau khi đã khóa staff row (S1-05).
     * {@code excludeBookingId} cho phép bỏ qua chính booking đang sửa.
     */
    @Query("""
            SELECT COUNT(b) FROM Booking b
            WHERE b.staff.id = :staffId
              AND b.id <> :excludeBookingId
              AND b.status NOT IN :terminalStatuses
              AND b.startAt < :candidateBufferEnd
              AND b.bufferEndAt > :candidateStart
            """)
    long countOverlapping(@Param("staffId") Long staffId,
                          @Param("excludeBookingId") Long excludeBookingId,
                          @Param("candidateStart") LocalDateTime candidateStart,
                          @Param("candidateBufferEnd") LocalDateTime candidateBufferEnd,
                          @Param("terminalStatuses") Collection<String> terminalStatuses);

    /**
     * Overlap check dạng locking read (S1-05): đọc dữ liệu mới nhất đã commit
     * thay vì snapshot REPEATABLE READ, nên transaction thứ hai sau khi lấy được
     * khóa staff row sẽ thấy booking vừa commit và bị chặn.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM Booking b
            WHERE b.staff.id IN :staffIds
              AND b.id <> :excludeBookingId
              AND b.startAt IS NOT NULL
              AND b.bufferEndAt IS NOT NULL
              AND b.status NOT IN :terminalStatuses
              AND b.startAt < :candidateBufferEnd
              AND b.bufferEndAt > :candidateStart
            """)
    List<Booking> findOverlappingForUpdate(@Param("staffIds") Collection<Long> staffIds,
                                           @Param("candidateStart") LocalDateTime candidateStart,
                                           @Param("candidateBufferEnd") LocalDateTime candidateBufferEnd,
                                           @Param("terminalStatuses") Collection<String> terminalStatuses,
                                           @Param("excludeBookingId") Long excludeBookingId);

    @EntityGraph(attributePaths = {"staff"})
    List<Booking> findByStaffIdAndStartAtBetweenOrderByStartAtAsc(
            Long staffId, LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = {"staff"})
    List<Booking> findByStaffIdOrderByStartAtDesc(Long staffId);
}
