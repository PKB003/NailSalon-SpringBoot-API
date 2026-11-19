package com.ttnails.booking_service.bookings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.audit.AuditService;
import com.ttnails.booking_service.availability.AvailabilityService;
import com.ttnails.booking_service.bookings.dto.BookingDTO;
import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.bookings.dto.BookingStatusHistoryDTO;
import com.ttnails.booking_service.bookings.dto.BookingStatusRequest;
import com.ttnails.booking_service.bookings.dto.GuestBookingRequest;
import com.ttnails.booking_service.bookings.dto.NewBookingRequest;
import com.ttnails.booking_service.bookings.dto.NoShowRequest;
import com.ttnails.booking_service.bookings.dto.RescheduleRequest;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.entity.BookingItem;
import com.ttnails.booking_service.bookings.entity.BookingStatusHistory;
import com.ttnails.booking_service.bookings.entity.BookingVersion;
import com.ttnails.booking_service.bookings.entity.CancellationRecord;
import com.ttnails.booking_service.bookings.repository.BookingItemRepository;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.bookings.repository.BookingStatusHistoryRepository;
import com.ttnails.booking_service.bookings.repository.BookingVersionRepository;
import com.ttnails.booking_service.bookings.repository.CancellationRecordRepository;
import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.common.dto.PageResponse;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.common.exception.NotFoundException;
import com.ttnails.booking_service.config.BookingPolicyProperties;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.staff.entity.Branch;
import com.ttnails.booking_service.staff.entity.Staff;
import com.ttnails.booking_service.staff.repository.BranchRepository;
import com.ttnails.booking_service.staff.repository.StaffRepository;
import com.ttnails.booking_service.users.dto.UserBookingRequest;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.UserRepository;
import com.ttnails.booking_service.waitlist.service.WaitlistService;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ đặt lịch (S1-05 + Sprint 2).
 *
 * <p>Luồng tạo booking mới:</p>
 * <ol>
 *   <li>Resolve danh sách dịch vụ (1..3, phương án A: 1 thợ làm liên tiếp).</li>
 *   <li>Kiểm tra no-show policy (S2-05) + chọn thợ + khóa staff row.</li>
 *   <li>Tính availability, kiểm tra overlap bằng locking read.</li>
 *   <li>Lưu booking (status {@code bestätigt}) + items + confirmation code + history.</li>
 * </ol>
 *
 * <p>Cancel/reschedule tuân chính sách 24h cấu hình được (D4) và ghi
 * {@code booking_status_history}, {@code booking_version}, {@code cancellation_record},
 * {@code audit_log}. Các endpoint {@code /booking/**} là legacy nhưng dùng chung luồng.</p>
 */
@org.springframework.stereotype.Service
public class BookingService {

    /** Giờ mở cửa mặc định — chỉ dùng khi chưa cấu hình branch (fallback). */
    public static final int OPENING_HOUR = 9;
    public static final int CLOSING_HOUR = 18;
    public static final int SLOT_MINUTES = 30;
    /** @deprecated S1-05 thay bằng mô hình theo thợ (mỗi thợ 1 lịch tại 1 thời điểm). */
    @Deprecated
    public static final int SLOT_CAPACITY = 10;

    private static final List<String> TERMINAL_STATUSES = BookingStatus.TERMINAL_LIST;
    private static final int MAX_CONFIRMATION_ATTEMPTS = 10;

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingStatusHistoryRepository historyRepository;
    private final BookingVersionRepository versionRepository;
    private final CancellationRecordRepository cancellationRecordRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final AvailabilityService availabilityService;
    private final BookingMapper bookingMapper;
    private final ConfirmationCodeService confirmationCodeService;
    private final CancellationPolicyService cancellationPolicyService;
    private final NoShowPolicyService noShowPolicyService;
    private final AuditService auditService;
    private final WaitlistService waitlistService;
    private final BookingPolicyProperties policyProperties;
    private final ObjectMapper objectMapper;

    public BookingService(BookingRepository bookingRepository,
                          BookingItemRepository bookingItemRepository,
                          BookingStatusHistoryRepository historyRepository,
                          BookingVersionRepository versionRepository,
                          CancellationRecordRepository cancellationRecordRepository,
                          UserRepository userRepository,
                          ServiceRepository serviceRepository,
                          StaffRepository staffRepository,
                          BranchRepository branchRepository,
                          AvailabilityService availabilityService,
                          BookingMapper bookingMapper,
                          ConfirmationCodeService confirmationCodeService,
                          CancellationPolicyService cancellationPolicyService,
                          NoShowPolicyService noShowPolicyService,
                          AuditService auditService,
                          WaitlistService waitlistService,
                          BookingPolicyProperties policyProperties,
                          ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.historyRepository = historyRepository;
        this.versionRepository = versionRepository;
        this.cancellationRecordRepository = cancellationRecordRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.staffRepository = staffRepository;
        this.branchRepository = branchRepository;
        this.availabilityService = availabilityService;
        this.bookingMapper = bookingMapper;
        this.confirmationCodeService = confirmationCodeService;
        this.cancellationPolicyService = cancellationPolicyService;
        this.noShowPolicyService = noShowPolicyService;
        this.auditService = auditService;
        this.waitlistService = waitlistService;
        this.policyProperties = policyProperties;
        this.objectMapper = objectMapper;
    }

    // ==================== Tạo booking (S1-05 + S2-01/05/07) ====================

    /**
     * POST /bookings — tạo booking (có token sẽ tự gắn user, không token vẫn đặt như guest).
     *
     * <p>Isolation READ_COMMITTED để sau khi lấy được khóa staff row, truy vấn kiểm tra
     * overlap đọc được dữ liệu vừa commit của transaction thắng trước đó (MySQL mặc định
     * REPEATABLE READ sẽ đọc snapshot cũ → lọt double booking).</p>
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse createBooking(NewBookingRequest request, Jwt jwt) {
        List<Long> serviceIds = resolveServiceIds(request);
        List<Service> services = loadServices(serviceIds);
        LocalDateTime start = parseStart(request.getDate(), request.getTime());

        User user = resolveUser(jwt);
        boolean staff = hasAnyRole(jwt, "ADMIN", "MANAGER", "RECEPTIONIST");
        boolean overrideNoShow = request.isOverrideNoShow() && staff;
        noShowPolicyService.assertAllowed(request.getPhone(),
                user != null ? user.getId() : null, overrideNoShow);

        String fullName = request.getName() != null ? request.getName().trim() : null;
        Booking booking = new Booking();
        booking.setFirstName(firstNameOf(fullName));
        booking.setLastName(lastNameOf(fullName));
        booking.setEmail(request.getEmail());
        booking.setPhone(request.getPhone());
        booking.setNote(request.getNote());
        booking.setUser(user);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmationCode(nextConfirmationCode());

        List<BookingItem> items = assignAndPersist(booking, services, start,
                request.getStaffId(), null);

        recordHistory(booking, null, BookingStatus.CONFIRMED, jwt, "Termin erstellt");
        audit("BOOKING_CREATE", booking, null, snapshot(booking), null);
        return bookingMapper.toResponse(booking, items);
    }

    /**
     * Chọn thợ + khóa row + lưu booking & items. Dùng chung cho tạo mới và đổi lịch.
     *
     * @param excludeBookingId booking hiện tại (khi đổi giờ) — bỏ qua khi kiểm tra overlap
     * @return danh sách item đã lưu
     */
    private List<BookingItem> assignAndPersist(Booking booking, List<Service> services,
                                               LocalDateTime start, Long requestedStaffId,
                                               Long excludeBookingId) {
        if (services == null || services.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        LocalDate date = start.toLocalDate();

        int totalDuration = services.stream().mapToInt(Service::effectiveDuration).sum();
        int requiredSpan = services.stream()
                .mapToInt(s -> s.effectiveDuration() + s.effectiveBuffer()).sum();
        LocalDateTime end = start.plusMinutes(totalDuration);
        LocalDateTime bufferEnd = start.plusMinutes(requiredSpan);

        validateWithinOpeningHours(start, totalDuration);

        AvailabilityService.DayAvailability day =
                availabilityService.computeDayForServices(date, services, requestedStaffId, excludeBookingId);

        List<Long> candidates = availabilityService.candidateStaffForSlot(day, start, requestedStaffId);
        if (candidates.isEmpty()) {
            List<Long> requestedIds = services.stream().map(Service::getId).toList();
            boolean eligible = requestedStaffId != null
                    ? availabilityService.canStaffWorkOn(requestedStaffId, requestedIds, date)
                    : availabilityService.anyStaffCanWorkOn(day.branch().getId(), requestedIds, date);
            throw new AppException(eligible ? ErrorCode.SLOT_TAKEN : ErrorCode.NO_STAFF_AVAILABLE);
        }

        Staff chosen = lockAndPickStaff(candidates, start, bufferEnd, excludeBookingId);
        if (chosen == null) {
            throw new AppException(ErrorCode.SLOT_TAKEN);
        }

        BigDecimal totalPrice = services.stream()
                .map(s -> s.getPrice() != null ? BigDecimal.valueOf(s.getPrice()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = services.stream()
                .map(Service::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst().orElse("EUR");

        booking.setStaff(chosen);
        booking.setStartAt(start);
        booking.setEndAt(end);
        booking.setBufferEndAt(bufferEnd);
        // preferredDateTime là tên field legacy của frontend — giữ đồng bộ với startAt
        booking.setPreferredDateTime(start);
        booking.setEmployee(chosen.getDisplayName());
        booking.setServiceId(services.get(0).getId());
        booking.setService(services.stream().map(Service::getName).collect(Collectors.joining(" + ")));
        booking.setPriceSnapshot(totalPrice);
        booking.setCurrency(currency);
        if (booking.getStatus() == null) {
            booking.setStatus(BookingStatus.CONFIRMED);
        }
        if (booking.getConfirmationCode() == null) {
            booking.setConfirmationCode(nextConfirmationCode());
        }
        booking = bookingRepository.save(booking);

        List<BookingItem> items = new ArrayList<>();
        LocalDateTime cursor = start;
        for (Service service : services) {
            LocalDateTime itemStart = cursor;
            LocalDateTime itemEnd = itemStart.plusMinutes(service.effectiveDuration());
            items.add(BookingItem.builder()
                    .booking(booking)
                    .service(service)
                    .staff(chosen)
                    .startAt(itemStart)
                    .endAt(itemEnd)
                    .duration(service.effectiveDuration())
                    .price(service.getPrice() != null
                            ? BigDecimal.valueOf(service.getPrice()) : BigDecimal.ZERO)
                    .currency(service.getCurrency() != null ? service.getCurrency() : currency)
                    .build());
            // Dịch vụ chạy liên tiếp: end_at = start + Σduration (buffer giữ ở cuối).
            cursor = itemEnd;
        }
        return bookingItemRepository.saveAll(items);
    }

    /**
     * Khóa staff row theo thứ tự id ASC (tránh deadlock), sau đó kiểm tra overlap
     * bằng locking read. Trả thợ đầu tiên còn trống theo thứ tự ưu tiên
     * (ít phút bận nhất → id ASC), hoặc {@code null} nếu tất cả đã kín.
     */
    private Staff lockAndPickStaff(List<Long> candidates, LocalDateTime start,
                                   LocalDateTime bufferEnd, Long excludeBookingId) {
        List<Long> lockOrder = candidates.stream().distinct().sorted().toList();
        for (Long staffId : lockOrder) {
            staffRepository.findByIdForUpdate(staffId)
                    .orElseThrow(() -> new NotFoundException("Staff not found with id: " + staffId));
        }
        List<Booking> overlaps = bookingRepository.findOverlappingForUpdate(
                lockOrder, start, bufferEnd, TERMINAL_STATUSES,
                excludeBookingId != null ? excludeBookingId : -1L);
        Set<Long> busyStaffIds = overlaps.stream()
                .filter(b -> b.getStaff() != null)
                .map(b -> b.getStaff().getId())
                .collect(Collectors.toSet());
        return candidates.stream()
                .distinct()
                .filter(id -> !busyStaffIds.contains(id))
                .findFirst()
                .flatMap(staffRepository::findById)
                .orElse(null);
    }

    private String nextConfirmationCode() {
        for (int attempt = 0; attempt < MAX_CONFIRMATION_ATTEMPTS; attempt++) {
            String code = confirmationCodeService.generate();
            if (bookingRepository.findByConfirmationCodeIgnoreCase(code).isEmpty()) {
                return code;
            }
        }
        throw new AppException(ErrorCode.INTERNAL_ERROR);
    }

    // ==================== Legacy API (/booking/**) ====================

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDTO createBookingForGuest(GuestBookingRequest request) {
        Service service = resolveServiceByName(request.getService());
        Booking booking = new Booking();
        booking.setFirstName(request.getFirstName());
        booking.setLastName(request.getLastName());
        booking.setEmail(request.getEmail());
        booking.setPhone(request.getPhone());
        booking.setNote(request.getNote());
        booking.setStatus(BookingStatus.CONFIRMED);
        assignAndPersist(booking, List.of(service), request.getPreferredDateAndTime(), null, null);
        recordHistory(booking, null, BookingStatus.CONFIRMED, null, "Termin erstellt (legacy)");
        return new BookingDTO(booking);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDTO createBookingForUser(UserBookingRequest request, Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaim("id"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        Service service = resolveServiceByName(request.getService());
        Booking booking = new Booking();
        booking.setFirstName(user.getName());
        booking.setLastName(null);
        booking.setEmail(user.getEmail());
        booking.setPhone(user.getPhone());
        booking.setNote(request.getNote());
        booking.setUser(user);
        booking.setStatus(BookingStatus.CONFIRMED);
        assignAndPersist(booking, List.of(service), request.getPreferredDateAndTime(), null, null);
        recordHistory(booking, null, BookingStatus.CONFIRMED, jwt, "Termin erstellt");
        return new BookingDTO(booking);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDTO updateBookingForAdmin(Long id, GuestBookingRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        assertNotTerminal(booking);
        rescheduleLegacy(booking, request.getService(), request.getPreferredDateAndTime(), request.getNote());
        return new BookingDTO(booking);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDTO updateBookingForUser(Long id, UserBookingRequest request, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (booking.getUser() == null || !booking.getUser().getId().equals(UUID.fromString(jwt.getClaim("id"))))
            throw new AppException(ErrorCode.UNAUTHORIZED);
        assertNotTerminal(booking);
        rescheduleLegacy(booking, request.getService(), request.getPreferredDateAndTime(), request.getNote());
        return new BookingDTO(booking);
    }

    /** Đổi lịch (legacy): giữ thợ cũ nếu có, validate lại qua engine. */
    private void rescheduleLegacy(Booking booking, String serviceName, LocalDateTime newStart, String note) {
        Service service = resolveServiceByName(serviceName);
        Long staffId = booking.getStaff() != null ? booking.getStaff().getId() : null;
        if (note != null) booking.setNote(note);
        bookingItemRepository.deleteAll(bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
        if (booking.getStatus() == null) {
            booking.setStatus(BookingStatus.CONFIRMED);
        }
        assignAndPersist(booking, List.of(service), newStart, staffId, booking.getId());
    }

    private Service resolveServiceByName(String name) {
        if (name == null || name.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return serviceRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Service not found: " + name));
    }

    // ==================== Truy vấn ====================

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    public List<BookingDTO> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(BookingDTO::new).collect(Collectors.toList());
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    public BookingDTO getBookingById(Long id) {
        return bookingRepository.findById(id)
                .map(BookingDTO::new)
                .orElseThrow(() -> new NotFoundException("Can't find anything with id:" + id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    public List<BookingDTO> getBookingByUserId(UUID uuid) {
        return bookingRepository.findByUserId(uuid).stream()
                .map(BookingDTO::new).collect(Collectors.toList());
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public void deleteBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));
        assertNotTerminal(booking);
        bookingRepository.deleteById(id);
    }

    /** GET /bookings — toàn bộ booking, giữ shape array cũ (ADMIN/MANAGER/RECEPTIONIST). */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookingsResponse() {
        return bookingRepository.findAll(Sort.by(Sort.Direction.DESC, "preferredDateTime"))
                .stream().map(bookingMapper::toResponse).collect(Collectors.toList());
    }

    /**
     * S2-09: danh sách admin có filter + phân trang (chuẩn {@link PageResponse}).
     *
     * <p>Controller chỉ gọi method này khi client gửi {@code page}; nếu không vẫn trả
     * array như cũ để không phá frontend S1.</p>
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> searchBookings(String status, Long staffId, Long serviceId,
                                                        LocalDate from, LocalDate to, String query,
                                                        int page, int size, String sort) {
        Specification<Booking> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (staffId != null) {
                predicates.add(cb.equal(root.get("staff").get("id"), staffId));
            }
            if (serviceId != null) {
                predicates.add(cb.equal(root.get("serviceId"), serviceId));
            }
            if (from != null || to != null) {
                @SuppressWarnings("unchecked")
                Expression<LocalDateTime> effectiveStart = cb.coalesce(
                        root.get("startAt"), root.get("preferredDateTime"));
                if (from != null) {
                    predicates.add(cb.greaterThanOrEqualTo(effectiveStart, from.atStartOfDay()));
                }
                if (to != null) {
                    predicates.add(cb.lessThanOrEqualTo(effectiveStart, to.atTime(23, 59, 59)));
                }
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(root.get("phone")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Booking> result = bookingRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), clampSize(size), parseSort(sort)));
        return PageResponse.from(result.map(bookingMapper::toResponse));
    }

    /** GET /bookings/my — booking của khách đang đăng nhập. */
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaim("id"));
        return bookingRepository.findByUserId(userId).stream()
                .sorted(java.util.Comparator.comparing(Booking::getPreferredDateTime,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed())
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }

    /** GET /bookings/{id}/history — lịch sử trạng thái (chủ booking hoặc staff). */
    @Transactional(readOnly = true)
    public List<BookingStatusHistoryDTO> getHistory(Long id, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));
        assertCanView(booking, jwt);
        return historyRepository.findByBookingIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(BookingStatusHistoryDTO::from)
                .toList();
    }

    /** GET /bookings/availability — legacy, deprecated: dùng {@code GET /availability}. */
    @Deprecated
    public List<String> getAvailability(LocalDate date) {
        return availabilityService.legacySlots(date);
    }

    // ==================== Đổi trạng thái / hủy / no-show ====================

    /**
     * PATCH /bookings/:id/cancel — chủ booking hoặc staff/admin.
     *
     * <p>D4: huỷ trễ là {@code WARN} (vẫn cho huỷ + ghi nhận late-cancel), có thể bật
     * {@code BLOCK}/{@code FEE} bằng cấu hình DB mà không cần deploy.</p>
     */
    @Transactional
    public BookingResponse cancelBooking(Long id, Jwt jwt, String reason) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));
        boolean staffRole = hasAnyRole(jwt, "ADMIN", "MANAGER", "RECEPTIONIST");
        if (!staffRole) {
            assertOwner(booking, jwt);
        }
        return doCancel(booking, staffRole, jwt, reason);
    }

    private BookingResponse doCancel(Booking booking, boolean staffRole, Jwt jwt, String reason) {
        String current = currentStatus(booking);
        if (BookingStatus.CANCELLED.equals(current)) {
            throw new AppException(ErrorCode.BOOKING_ALREADY_CANCELLED);
        }
        if (BookingStatus.COMPLETED.equals(current)) {
            throw new AppException(ErrorCode.BOOKING_ALREADY_COMPLETED);
        }
        if (BookingStatus.NO_SHOW.equals(current) || BookingStatus.EXPIRED.equals(current)) {
            throw new AppException(ErrorCode.BOOKING_TERMINAL);
        }

        LocalDateTime start = startOf(booking);
        LocalDateTime now = LocalDateTime.now();
        if (!staffRole && start != null && start.isBefore(now)) {
            throw new AppException(ErrorCode.APPOINTMENT_PASSED);
        }

        CancellationPolicyService.ResolvedPolicy policy = cancellationPolicyService.resolve(booking);
        boolean late = cancellationPolicyService.isLate(start, policy, now);
        if (!staffRole && late && "BLOCK".equalsIgnoreCase(policy.lateCancelAction())) {
            throw new AppException(ErrorCode.LATE_CANCEL_BLOCKED);
        }

        BigDecimal fee = staffRole ? BigDecimal.ZERO
                : cancellationPolicyService.computeLateFee(booking.getPriceSnapshot(), policy, late);
        Map<String, Object> before = snapshot(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        booking = bookingRepository.saveAndFlush(booking);
        recordHistory(booking, current, BookingStatus.CANCELLED, jwt, reason);

        CancellationRecord record = CancellationRecord.builder()
                .booking(booking)
                .requestedBy(actorId(jwt))
                .requestedByRole(actorRole(jwt))
                .reason(reason)
                .late(late)
                .policySnapshot(policy.snapshot())
                .feeAmount(fee)
                .refundAmount(BigDecimal.ZERO)
                .createdAt(now)
                .build();
        cancellationRecordRepository.save(record);

        audit("BOOKING_CANCEL", booking, before, snapshot(booking), reason);
        notifyWaitlistSafely(booking.getStartAt() != null ? booking.getStartAt().toLocalDate() : null,
                currentServiceIds(booking));

        BookingResponse response = bookingMapper.toResponse(booking,
                bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
        BookingResponse.CancellationInfo info = new BookingResponse.CancellationInfo();
        info.setLate(late);
        info.setFeeAmount(fee);
        info.setRefundAmount(BigDecimal.ZERO);
        info.setReason(reason);
        info.setRequestedByRole(record.getRequestedByRole());
        info.setCancelledAt(now);
        response.setCancellation(info);
        response.setVersionNo(currentVersion(booking.getId()));
        return response;
    }

    /** PATCH /bookings/:id/status — staff/admin, tuân ma trận transition chuẩn production. */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public BookingResponse updateStatus(Long id, BookingStatusRequest request, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));
        String target = request.getStatus();
        if (!BookingStatus.isKnown(target)) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
        String current = currentStatus(booking);
        if (!BookingStatus.isTransitionAllowed(current, target)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (BookingStatus.CANCELLED.equals(target)) {
            // Uỷ quyền cho luồng huỷ để ghi cancellation_record + policy + audit.
            return doCancel(booking, true, jwt, request.getReason());
        }
        if (BookingStatus.NO_SHOW.equals(target)) {
            NoShowRequest noShow = new NoShowRequest();
            noShow.setReason(request.getReason());
            return doMarkNoShow(booking, noShow, jwt);
        }

        LocalDateTime start = startOf(booking);
        LocalDateTime now = LocalDateTime.now();
        validateTransitionGuards(current, target, start, now, request.getReason());

        Map<String, Object> before = snapshot(booking);
        boolean changed = !target.equals(current);
        booking.setStatus(target);
        if (request.getEmployee() != null && !request.getEmployee().isBlank()) {
            booking.setEmployee(request.getEmployee());
            changed = true;
        }
        booking = bookingRepository.save(booking);
        if (!target.equals(current)) {
            recordHistory(booking, current, target, jwt, request.getReason());
        }
        if (changed) {
            audit("BOOKING_STATUS_UPDATE", booking, before, snapshot(booking), request.getReason());
        }
        BookingResponse response = bookingMapper.toResponse(booking,
                bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
        response.setVersionNo(currentVersion(booking.getId()));
        return response;
    }

    /** PATCH /bookings/:id/no-show — đánh dấu khách không đến (S2-05). */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public BookingResponse markNoShow(Long id, NoShowRequest request, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));
        return doMarkNoShow(booking, request, jwt);
    }

    private BookingResponse doMarkNoShow(Booking booking, NoShowRequest request, Jwt jwt) {
        String current = currentStatus(booking);
        if (BookingStatus.NO_SHOW.equals(current)) {
            return bookingMapper.toResponse(booking,
                    bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
        }
        if (BookingStatus.isTerminal(current)) {
            throw new AppException(ErrorCode.BOOKING_TERMINAL);
        }
        if (!BookingStatus.CONFIRMED.equals(current) && !BookingStatus.CHECKED_IN.equals(current)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        LocalDateTime start = startOf(booking);
        if (start != null && LocalDateTime.now().isBefore(start)) {
            if (!request.isOverride()) {
                throw new AppException(ErrorCode.NO_SHOW_TOO_EARLY);
            }
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new AppException(ErrorCode.REASON_REQUIRED);
            }
        }
        Map<String, Object> before = snapshot(booking);
        booking.setStatus(BookingStatus.NO_SHOW);
        booking = bookingRepository.save(booking);
        recordHistory(booking, current, BookingStatus.NO_SHOW, jwt, request.getReason());
        audit("BOOKING_NO_SHOW", booking, before, snapshot(booking), request.getReason());
        return bookingMapper.toResponse(booking,
                bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
    }

    /**
     * PATCH /bookings/:id/reschedule — đổi lịch (S2-02).
     *
     * <p>Khách chỉ đổi được khi còn ≥ {@code free_cancel_hours}; staff override được
     * nhưng phải gửi lý do. Mỗi lần đổi lưu snapshot lịch cũ vào {@code booking_version}.</p>
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse rescheduleBooking(Long id, RescheduleRequest request, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));
        boolean staff = hasAnyRole(jwt, "ADMIN", "MANAGER", "RECEPTIONIST");
        if (!staff) {
            assertOwner(booking, jwt);
        }
        String current = currentStatus(booking);
        if (BookingStatus.isTerminal(current)) {
            throw new AppException(ErrorCode.BOOKING_TERMINAL);
        }
        if (!BookingStatus.CONFIRMED.equals(current) && !BookingStatus.PENDING_PAYMENT.equals(current)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        LocalDateTime oldStart = startOf(booking);
        LocalDateTime newStart = parseStart(request.getDate(), request.getTime());
        CancellationPolicyService.ResolvedPolicy policy = cancellationPolicyService.resolve(booking);
        boolean late = cancellationPolicyService.isLate(oldStart, policy, LocalDateTime.now());
        if (late) {
            if (!staff) {
                throw new AppException(ErrorCode.RESCHEDULE_TOO_LATE);
            }
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new AppException(ErrorCode.REASON_REQUIRED);
            }
        }

        List<Long> oldServiceIds = currentServiceIds(booking);
        Integer oldVersion = currentVersion(booking.getId());
        Map<String, Object> before = snapshot(booking);
        saveVersion(booking, oldVersion, request.getReason(), before, actorId(jwt));

        List<Long> serviceIds = request.getServiceIds() != null && !request.getServiceIds().isEmpty()
                ? request.getServiceIds().stream().filter(Objects::nonNull).distinct().toList()
                : oldServiceIds;
        if (serviceIds.size() > 3) {
            throw new AppException(ErrorCode.TOO_MANY_SERVICES);
        }
        List<Service> services = loadServices(serviceIds);
        Long staffId = request.getStaffId() != null ? request.getStaffId()
                : booking.getStaff() != null ? booking.getStaff().getId() : null;

        bookingItemRepository.deleteAll(bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
        if (request.getNote() != null) {
            booking.setNote(request.getNote());
        }
        assignAndPersist(booking, services, newStart, staffId, booking.getId());
        booking = bookingRepository.saveAndFlush(booking);
        recordHistory(booking, current, current, jwt, "Umbuchung: " + request.getReason());

        audit("BOOKING_RESCHEDULE", booking, before, snapshot(booking), request.getReason());
        notifyWaitlistSafely(oldStart != null ? oldStart.toLocalDate() : null, oldServiceIds);

        BookingResponse response = bookingMapper.toResponse(booking,
                bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
        response.setVersionNo(currentVersion(booking.getId()));
        return response;
    }

    // ==================== Helpers ====================

    private void validateTransitionGuards(String current, String target, LocalDateTime start,
                                          LocalDateTime now, String reason) {
        if (BookingStatus.CHECKED_IN.equals(target) && start != null
                && now.isBefore(start.minusHours(Math.max(policyProperties.getCheckInWindowHours(), 1)))) {
            throw new AppException(ErrorCode.CHECK_IN_TOO_EARLY);
        }
        if (BookingStatus.IN_SERVICE.equals(target) && start != null && start.isBefore(now)
                && !BookingStatus.IN_SERVICE.equals(current)) {
            throw new AppException(ErrorCode.APPOINTMENT_PASSED);
        }
        if (BookingStatus.COMPLETED.equals(target) && start != null && start.isAfter(now)) {
            throw new AppException(ErrorCode.COMPLETION_TOO_EARLY);
        }
        if (BookingStatus.NO_SHOW.equals(target) && start != null && now.isBefore(start)) {
            throw new AppException(ErrorCode.NO_SHOW_TOO_EARLY);
        }
        if (BookingStatus.EXPIRED.equals(target)
                && (reason == null || reason.isBlank())) {
            throw new AppException(ErrorCode.REASON_REQUIRED);
        }
    }

    private void recordHistory(Booking booking, String from, String to, Jwt jwt, String reason) {
        BookingStatusHistory history = BookingStatusHistory.builder()
                .booking(booking)
                .fromStatus(from)
                .toStatus(to)
                .actorId(actorId(jwt))
                .actorLabel(actorLabel(jwt))
                .actorRole(actorRole(jwt))
                .reason(trimTo(reason, 500))
                .createdAt(LocalDateTime.now())
                .build();
        historyRepository.save(history);
    }

    private void saveVersion(Booking booking, Integer oldVersion, String reason,
                             Map<String, Object> snapshot, String actorId) {
        BookingVersion version = BookingVersion.builder()
                .booking(booking)
                .versionNo(oldVersion != null ? oldVersion + 1 : 1)
                .reason(trimTo(reason, 255))
                .snapshotJson(toJson(snapshot))
                .createdBy(actorId)
                .createdAt(LocalDateTime.now())
                .build();
        versionRepository.save(version);
    }

    private Integer currentVersion(Long bookingId) {
        return versionRepository.findMaxVersionNo(bookingId).orElse(0);
    }

    private List<Long> currentServiceIds(Booking booking) {
        List<BookingItem> items = bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId());
        if (!items.isEmpty()) {
            return items.stream().map(item -> item.getService().getId()).distinct().toList();
        }
        return booking.getServiceId() != null ? List.of(booking.getServiceId()) : List.of();
    }

    private void notifyWaitlistSafely(LocalDate date, Collection<Long> serviceIds) {
        if (date == null || serviceIds == null || serviceIds.isEmpty()) return;
        try {
            waitlistService.notifyReleasedSlot(date, serviceIds);
        } catch (RuntimeException ignored) {
            // Thông báo waitlist không được làm hỏng giao dịch huỷ/đổi lịch.
        }
    }

    private Map<String, Object> snapshot(Booking booking) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", booking.getId());
        map.put("status", booking.getStatus());
        map.put("serviceId", booking.getServiceId());
        map.put("service", booking.getService());
        map.put("staffId", booking.getStaff() != null ? booking.getStaff().getId() : null);
        map.put("startAt", booking.getStartAt());
        map.put("endAt", booking.getEndAt());
        map.put("bufferEndAt", booking.getBufferEndAt());
        map.put("priceSnapshot", booking.getPriceSnapshot());
        map.put("currency", booking.getCurrency());
        map.put("confirmationCode", booking.getConfirmationCode());
        return map;
    }

    private void audit(String action, Booking booking, Object before, Object after, String reason) {
        try {
            auditService.record(action, "BOOKING", booking.getId(), before, after, reason);
        } catch (RuntimeException ignored) {
            // Audit không được chặn nghiệp vụ.
        }
    }

    private LocalDateTime startOf(Booking booking) {
        return booking.getStartAt() != null ? booking.getStartAt() : booking.getPreferredDateTime();
    }

    private String currentStatus(Booking booking) {
        return booking.getStatus() != null ? booking.getStatus() : BookingStatus.IN_SERVICE;
    }

    private void assertOwner(Booking booking, Jwt jwt) {
        if (jwt == null || jwt.getClaim("id") == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(jwt.getClaim("id"));
        if (booking.getUser() == null || !booking.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void assertCanView(Booking booking, Jwt jwt) {
        if (hasAnyRole(jwt, "ADMIN", "MANAGER", "RECEPTIONIST")) return;
        assertOwner(booking, jwt);
    }

    /**
     * Guards trạng thái terminal — áp dụng cho mọi endpoint sửa/xóa booking.
     * Lịch "abgeschlossen"/"storniert"/"nicht erschienen"/"abgelaufen" là bất biến.
     */
    private void assertNotTerminal(Booking booking) {
        String status = currentStatus(booking);
        if (BookingStatus.CANCELLED.equals(status)) {
            throw new AppException(ErrorCode.BOOKING_ALREADY_CANCELLED);
        }
        if (BookingStatus.COMPLETED.equals(status)) {
            throw new AppException(ErrorCode.BOOKING_ALREADY_COMPLETED);
        }
        if (BookingStatus.isTerminal(status)) {
            throw new AppException(ErrorCode.BOOKING_TERMINAL);
        }
    }

    private boolean hasAnyRole(Jwt jwt, String... roles) {
        String scope = jwt != null ? jwt.getClaimAsString("scope") : null;
        if (scope == null || scope.isBlank()) return false;
        List<String> granted = List.of(scope.split(" "));
        for (String role : roles) {
            if (granted.contains(role)) return true;
        }
        return false;
    }

    private User resolveUser(Jwt jwt) {
        if (jwt != null && jwt.getClaim("id") != null) {
            return userRepository.findById(UUID.fromString(jwt.getClaim("id"))).orElse(null);
        }
        return null;
    }

    private String actorId(Jwt jwt) {
        return jwt != null && jwt.getClaim("id") != null ? jwt.getClaimAsString("id") : null;
    }

    private String actorLabel(Jwt jwt) {
        if (jwt == null) return "guest";
        return jwt.getSubject() != null ? jwt.getSubject() : "system";
    }

    private String actorRole(Jwt jwt) {
        String role = actorRoleRaw(jwt);
        return role != null ? role : "GUEST";
    }

    private String actorRoleRaw(Jwt jwt) {
        String scope = jwt != null ? jwt.getClaimAsString("scope") : null;
        if (scope == null || scope.isBlank()) return null;
        return scope.split(" ")[0];
    }

    private List<Long> resolveServiceIds(NewBookingRequest request) {
        List<Long> ids = new ArrayList<>();
        if (request.getServiceIds() != null) {
            request.getServiceIds().stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty() && request.getServiceId() != null) {
            ids.add(request.getServiceId());
        }
        if (ids.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.size() > 3) {
            throw new AppException(ErrorCode.TOO_MANY_SERVICES);
        }
        return distinct;
    }

    private List<Service> loadServices(List<Long> serviceIds) {
        var byId = serviceRepository.findByIdInAndActiveTrue(serviceIds).stream()
                .collect(Collectors.toMap(Service::getId, s -> s));
        List<Service> ordered = new ArrayList<>();
        for (Long id : serviceIds) {
            Service service = byId.get(id);
            if (service == null) {
                throw new NotFoundException("Service not found with id: " + id);
            }
            ordered.add(service);
        }
        return ordered;
    }

    private LocalDateTime parseStart(String dateText, String timeText) {
        LocalDate date;
        LocalTime time;
        try {
            date = LocalDate.parse(dateText);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new AppException(ErrorCode.INVALID_DATE);
        }
        try {
            time = LocalTime.parse(timeText);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new AppException(ErrorCode.INVALID_TIME);
        }
        LocalDateTime start = date.atTime(time);
        if (!start.isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.INVALID_DATE);
        }
        return start;
    }

    /**
     * Chặn giờ ngoài giờ mở cửa và phút lệch bước slot (giữ nguyên error code
     * {@code OUTSIDE_OPENING_HOURS} của contract cũ).
     */
    private void validateWithinOpeningHours(LocalDateTime start, int totalDurationMinutes) {
        Branch branch = branchRepository.findFirstByActiveTrueOrderByIdAsc().orElse(null);
        LocalTime open = branch != null ? branch.getOpenTime() : LocalTime.of(OPENING_HOUR, 0);
        LocalTime close = branch != null ? branch.getCloseTime() : LocalTime.of(CLOSING_HOUR, 0);
        int step = branch != null && branch.getSlotStepMinutes() > 0
                ? branch.getSlotStepMinutes() : SLOT_MINUTES;

        LocalTime time = start.toLocalTime();
        if (time.isBefore(open) || !time.isBefore(close)
                || start.getSecond() != 0 || start.getNano() != 0) {
            throw new AppException(ErrorCode.OUTSIDE_OPENING_HOURS);
        }
        if (start.plusMinutes(totalDurationMinutes).isAfter(start.toLocalDate().atTime(close))) {
            throw new AppException(ErrorCode.OUTSIDE_OPENING_HOURS);
        }
        long offset = Duration.between(start.toLocalDate().atTime(open), start).toMinutes();
        if (offset % step != 0) {
            throw new AppException(ErrorCode.INVALID_SLOT);
        }
    }

    private int clampSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, 100);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "preferredDateTime");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Set<String> allowed = Set.of("preferredDateTime", "startAt", "status", "id");
        if (!allowed.contains(field)) {
            field = "preferredDateTime";
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return null;
        int idx = fullName.indexOf(' ');
        return idx < 0 ? fullName : fullName.substring(0, idx);
    }

    private String lastNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return null;
        int idx = fullName.indexOf(' ');
        return idx < 0 ? null : fullName.substring(idx + 1).trim();
    }

    private String trimTo(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
