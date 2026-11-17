package com.ttnails.booking_service.bookings.service;

import com.ttnails.booking_service.availability.AvailabilityService;
import com.ttnails.booking_service.bookings.dto.BookingDTO;
import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.bookings.dto.BookingStatusRequest;
import com.ttnails.booking_service.bookings.dto.GuestBookingRequest;
import com.ttnails.booking_service.bookings.dto.NewBookingRequest;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.entity.BookingItem;
import com.ttnails.booking_service.bookings.repository.BookingItemRepository;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.common.exception.NotFoundException;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.staff.entity.Branch;
import com.ttnails.booking_service.staff.entity.Staff;
import com.ttnails.booking_service.staff.repository.BranchRepository;
import com.ttnails.booking_service.staff.repository.StaffRepository;
import com.ttnails.booking_service.users.dto.UserBookingRequest;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ đặt lịch (S1-05).
 *
 * <p>Luồng tạo booking mới:</p>
 * <ol>
 *   <li>Resolve danh sách dịch vụ (1..3, phương án A: 1 thợ làm liên tiếp).</li>
 *   <li>Tính availability bằng {@link AvailabilityService} (engine + DB).</li>
 *   <li>Khóa staff row theo id ASC, kiểm tra overlap bằng locking read
 *       → chỉ một transaction thắng trên cùng thợ + cùng khoảng giờ.</li>
 *   <li>Lưu {@link Booking} + {@link BookingItem} kèm snapshot giá.</li>
 * </ol>
 *
 * <p>Các endpoint {@code /booking/**} là legacy (deprecated) nhưng vẫn dùng chung
 * luồng trên để mọi booking mới đều có thợ + khung giờ chính xác.</p>
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

    private static final List<String> TERMINAL_STATUSES =
            List.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED);

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final AvailabilityService availabilityService;
    private final BookingMapper bookingMapper;

    public BookingService(BookingRepository bookingRepository,
                          BookingItemRepository bookingItemRepository,
                          UserRepository userRepository,
                          ServiceRepository serviceRepository,
                          StaffRepository staffRepository,
                          BranchRepository branchRepository,
                          AvailabilityService availabilityService,
                          BookingMapper bookingMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.staffRepository = staffRepository;
        this.branchRepository = branchRepository;
        this.availabilityService = availabilityService;
        this.bookingMapper = bookingMapper;
    }

    // ==================== Tạo booking (S1-05) ====================

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

        User user = null;
        if (jwt != null && jwt.getClaim("id") != null) {
            user = userRepository.findById(UUID.fromString(jwt.getClaim("id"))).orElse(null);
        }

        String fullName = request.getName() != null ? request.getName().trim() : null;
        Booking booking = new Booking();
        booking.setFirstName(firstNameOf(fullName));
        booking.setLastName(lastNameOf(fullName));
        booking.setEmail(request.getEmail());
        booking.setPhone(request.getPhone());
        booking.setNote(request.getNote());
        booking.setUser(user);

        List<BookingItem> items = assignAndPersist(booking, services, start, request.getStaffId(), null);
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
                availabilityService.computeDayForServices(date, services, requestedStaffId);

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
            booking.setStatus(BookingStatus.IN_PROGRESS);
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
            // Dịch vụ chạy liên tiếp: end_at = start + Σduration (buffer được giữ ở cuối
            // toàn bộ appointment — xem requiredSpan = Σ(duration + buffer)).
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
        java.util.Set<Long> busyStaffIds = overlaps.stream()
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

    private List<Long> resolveServiceIds(NewBookingRequest request) {
        List<Long> ids = new ArrayList<>();
        if (request.getServiceIds() != null) {
            request.getServiceIds().stream().filter(java.util.Objects::nonNull).forEach(ids::add);
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
            // Dịch vụ dài hơn phần còn lại của ngày → không thể hoàn thành trong giờ mở cửa
            throw new AppException(ErrorCode.OUTSIDE_OPENING_HOURS);
        }
        long offset = Duration.between(start.toLocalDate().atTime(open), start).toMinutes();
        if (offset % step != 0) {
            throw new AppException(ErrorCode.INVALID_SLOT);
        }
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
        assignAndPersist(booking, List.of(service), request.getPreferredDateAndTime(), null, null);
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
        assignAndPersist(booking, List.of(service), request.getPreferredDateAndTime(), null, null);
        return new BookingDTO(booking);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDTO updateBookingForAdmin(Long id, GuestBookingRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        assertNotTerminal(booking);
        reschedule(booking, request.getService(), request.getPreferredDateAndTime(), request.getNote());
        return new BookingDTO(booking);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDTO updateBookingForUser(Long id, UserBookingRequest request, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (booking.getUser() == null || !booking.getUser().getId().equals(UUID.fromString(jwt.getClaim("id"))))
            throw new AppException(ErrorCode.UNAUTHORIZED);
        assertNotTerminal(booking);
        reschedule(booking, request.getService(), request.getPreferredDateAndTime(), request.getNote());
        return new BookingDTO(booking);
    }

    /** Đổi lịch (legacy): giữ thợ cũ nếu có, validate lại qua engine. */
    private void reschedule(Booking booking, String serviceName, LocalDateTime newStart, String note) {
        Service service = resolveServiceByName(serviceName);
        Long staffId = booking.getStaff() != null ? booking.getStaff().getId() : null;
        if (note != null) booking.setNote(note);
        bookingItemRepository.deleteAll(bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
        booking.setStatus(BookingStatus.IN_PROGRESS);
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

    /** GET /bookings — toàn bộ booking (ADMIN/MANAGER/RECEPTIONIST). */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookingsResponse() {
        return bookingRepository.findAll(Sort.by(Sort.Direction.DESC, "preferredDateTime"))
                .stream().map(bookingMapper::toResponse).collect(Collectors.toList());
    }

    /** GET /bookings/my — booking của khách đang đăng nhập. */
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaim("id"));
        return bookingRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Booking::getPreferredDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }

    /** GET /bookings/availability — legacy, deprecated: dùng {@code GET /availability}. */
    @Deprecated
    public List<String> getAvailability(LocalDate date) {
        return availabilityService.legacySlots(date);
    }

    // ==================== Đổi trạng thái / hủy ====================

    /** PATCH /bookings/:id/cancel — chủ booking hoặc staff/admin → status = "storniert". */
    @Transactional
    public BookingResponse cancelBooking(Long id, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));

        boolean staffRole = hasAnyRole(jwt, "ADMIN", "MANAGER", "RECEPTIONIST");
        if (!staffRole) {
            UUID userId = UUID.fromString(jwt.getClaim("id"));
            if (booking.getUser() == null || !booking.getUser().getId().equals(userId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
        }
        // Guards trạng thái terminal — áp dụng cho CẢ admin (bất biến kế toán/doanh thu)
        if (BookingStatus.CANCELLED.equals(booking.getStatus())) {
            throw new AppException(ErrorCode.BOOKING_ALREADY_CANCELLED);
        }
        if (BookingStatus.COMPLETED.equals(booking.getStatus())) {
            throw new AppException(ErrorCode.BOOKING_ALREADY_COMPLETED);
        }
        LocalDateTime start = startOf(booking);
        if (!staffRole) {
            // Lịch đã qua giờ: khách không được cancel (admin xử lý no-show riêng)
            if (start != null && start.isBefore(LocalDateTime.now())) {
                throw new AppException(ErrorCode.APPOINTMENT_PASSED);
            }
            // Chính sách 24h: khách chỉ được tự hủy khi còn ít nhất 24h trước giờ hẹn
            if (start != null && start.isBefore(LocalDateTime.now().plusHours(24))) {
                throw new AppException(ErrorCode.CANCEL_TOO_LATE);
            }
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        return bookingMapper.toResponse(booking,
                bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
    }

    /** PATCH /bookings/:id/status — staff/admin, tuân ma trận transition chuẩn production. */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public BookingResponse updateStatus(Long id, BookingStatusRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));
        if (!BookingStatus.ALL.contains(request.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
        String current = booking.getStatus() != null ? booking.getStatus() : BookingStatus.IN_PROGRESS;
        // Ma trận transition trước: terminal (abgeschlossen/storniert) khóa mọi transition kể cả admin
        if (!BookingStatus.isTransitionAllowed(current, request.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        LocalDateTime start = startOf(booking);
        // Lịch đã qua mà cố set lùi về trạng thái đang xử lý → cấm (giữ toàn vẹn lịch sử)
        if (BookingStatus.IN_PROGRESS.equals(request.getStatus())
                && start != null
                && start.isBefore(LocalDateTime.now())
                && !BookingStatus.IN_PROGRESS.equals(current)) {
            throw new AppException(ErrorCode.APPOINTMENT_PASSED);
        }
        // Không cho đóng hoàn tất lịch chưa tới giờ (chống gian lận no-show/doanh thu ảo)
        if (BookingStatus.COMPLETED.equals(request.getStatus())
                && start != null
                && start.isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.COMPLETION_TOO_EARLY);
        }
        booking.setStatus(request.getStatus());
        if (request.getEmployee() != null && !request.getEmployee().isBlank()) {
            booking.setEmployee(request.getEmployee());
        }
        bookingRepository.save(booking);
        return bookingMapper.toResponse(booking,
                bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId()));
    }

    // ==================== Helpers ====================

    private LocalDateTime startOf(Booking booking) {
        return booking.getStartAt() != null ? booking.getStartAt() : booking.getPreferredDateTime();
    }

    /**
     * Guards trạng thái terminal — áp dụng cho mọi endpoint sửa/xóa booking.
     * Lịch "abgeschlossen"/"storniert" là bất biến kế toán: không được sửa, không được xóa mềm.
     */
    private void assertNotTerminal(Booking booking) {
        if (BookingStatus.CANCELLED.equals(booking.getStatus())) {
            throw new AppException(ErrorCode.BOOKING_ALREADY_CANCELLED);
        }
        if (BookingStatus.COMPLETED.equals(booking.getStatus())) {
            throw new AppException(ErrorCode.BOOKING_ALREADY_COMPLETED);
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
}
