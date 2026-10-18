package com.ttnails.booking_service.bookings.service;


import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.bookings.dto.BookingDTO;
import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.bookings.dto.BookingStatusRequest;
import com.ttnails.booking_service.bookings.dto.GuestBookingRequest;
import com.ttnails.booking_service.bookings.dto.NewBookingRequest;
import com.ttnails.booking_service.users.dto.UserBookingRequest;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.common.exception.NotFoundException;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {
    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    /**
     * Slot 30 phút, đếm TẤT CẢ booking trong slot (mọi dịch vụ) — đồng bộ tuyệt đối
     * với getAvailability(). Dùng pessimistic lock để chống race condition khi đặt đồng thời.
     */
    private void validateSlot(LocalDateTime preferredDateTime, Long excludeBookingId) {
        LocalDateTime slotStart = (preferredDateTime.getMinute() < 30
                ? preferredDateTime.withMinute(0) : preferredDateTime.withMinute(30))
                .withSecond(0).withNano(0);
        LocalDateTime slotEnd = slotStart.plusMinutes(SLOT_MINUTES);

        List<Booking> slotBookings = bookingRepository.findLockedBySlotWindow(slotStart, slotEnd);
        long count = slotBookings.stream()
                .filter(b -> excludeBookingId == null || !excludeBookingId.equals(b.getId()))
                .count();

        if (count >= SLOT_CAPACITY) throw new AppException(ErrorCode.SLOT_FULL);
    }

    /** Chặn đặt lịch ngoài giờ mở cửa (09:00–18:00) hoặc phút lệch bước 30 phút */
    private void validateOpeningHours(LocalDateTime preferred) {
        LocalTime open = LocalTime.of(OPENING_HOUR, 0);
        LocalTime close = LocalTime.of(CLOSING_HOUR, 0);
        LocalTime time = preferred.toLocalTime();
        if (time.isBefore(open) || !time.isBefore(close)
                || preferred.getMinute() % SLOT_MINUTES != 0 || preferred.getSecond() != 0) {
            throw new AppException(ErrorCode.OUTSIDE_OPENING_HOURS);
        }
    }

    private Booking buildBooking(Booking booking, String firstName, String lastName, String email, String phone,
                                 String service, LocalDateTime preferredDateTime, String note, User user) {
        booking.setFirstName(firstName);
        booking.setLastName(lastName);
        booking.setEmail(email);
        booking.setPhone(phone);
        booking.setService(service);
        booking.setPreferredDateTime(preferredDateTime);
        booking.setNote(note);
        booking.setUser(user);
        // Đồng bộ status mặc định cho cả booking legacy (trước đây lưu NULL)
        if (booking.getStatus() == null) {
            booking.setStatus(BookingStatus.IN_PROGRESS);
        }
        return booking;
    }

    private BookingDTO toDTO(Booking booking) {
        return new BookingDTO(booking);
    }

    @Transactional
    public BookingDTO createBookingForGuest(GuestBookingRequest request) {
        validateSlot(request.getPreferredDateAndTime(), null);
        Booking booking = new Booking();
        bookingRepository.save(
                buildBooking(booking,
                        request.getFirstName(),
                        request.getLastName(),
                        request.getEmail(),
                        request.getPhone(),
                        request.getService(),
                        request.getPreferredDateAndTime(),
                        request.getNote(),
                        null
                )
        );
        return toDTO(booking);
    }

    @Transactional
    public BookingDTO createBookingForUser(UserBookingRequest request, Jwt jwt) {
        String userId = jwt.getClaim("id");

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        validateSlot(request.getPreferredDateAndTime(), null);

        Booking booking = new Booking();
        bookingRepository.save(
                buildBooking(
                        booking,
                        user.getName(),
                        null,
                        user.getEmail(),
                        user.getPhone(),
                        request.getService(),
                        request.getPreferredDateAndTime(),
                        request.getNote(),
                        user
                )
        );
        return toDTO(booking);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public BookingDTO updateBookingForAdmin(Long id, GuestBookingRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        assertNotTerminal(booking);
        validateSlot(request.getPreferredDateAndTime(), id);
        bookingRepository.save(
                buildBooking(booking,
                        request.getFirstName(),
                        request.getLastName(),
                        request.getEmail(),
                        request.getPhone(),
                        request.getService(),
                        request.getPreferredDateAndTime(),
                        request.getNote(),
                        booking.getUser()
                )
        );
        return toDTO(booking);
    }

    @Transactional
    public BookingDTO updateBookingForUser(Long id, UserBookingRequest request, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (booking.getUser() == null || !booking.getUser().getId().equals(UUID.fromString(jwt.getClaim("id"))))
            throw new AppException(ErrorCode.UNAUTHORIZED);
        assertNotTerminal(booking);

        validateSlot(request.getPreferredDateAndTime(), id);

        booking.setService(request.getService());
        booking.setPreferredDateTime(request.getPreferredDateAndTime());
        booking.setNote(request.getNote());

        bookingRepository.save(booking);
        return toDTO(booking);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingDTO> getAllBookings() {
        return bookingRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public BookingDTO getBookingById(Long id) {
        return bookingRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new NotFoundException("Can't find anything with id:" + id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingDTO> getBookingByUserId(UUID uuid) {
        return bookingRepository.findByUserId(uuid).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));
        assertNotTerminal(booking);
        bookingRepository.deleteById(id);
    }

    // ==================== API contract (/bookings) ====================

    public static final int OPENING_HOUR = 9;
    public static final int CLOSING_HOUR = 18;
    public static final int SLOT_MINUTES = 30;
    public static final int SLOT_CAPACITY = 10;

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

    /** GET /bookings/availability?date=YYYY-MM-DD — chỉ trả slot còn trống ("09:00", "09:30", ...) */
    public List<String> getAvailability(LocalDate date) {
        List<String> slots = new ArrayList<>();
        if (date.isBefore(LocalDate.now())) return slots;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cursor = date.atTime(OPENING_HOUR, 0);
        LocalDateTime closing = date.atTime(CLOSING_HOUR, 0);

        while (cursor.isBefore(closing)) {
            boolean inFuture = cursor.isAfter(now);
            long booked = bookingRepository
                    .countByPreferredDateTimeGreaterThanEqualAndPreferredDateTimeLessThan(
                            cursor, cursor.plusMinutes(SLOT_MINUTES));
            if (inFuture && booked < SLOT_CAPACITY) {
                slots.add(cursor.toLocalTime().toString());
            }
            cursor = cursor.plusMinutes(SLOT_MINUTES);
        }
        return slots;
    }

    /** POST /bookings — tạo booking (có token sẽ tự gắn user, không token vẫn đặt như guest) */
    @Transactional
    public BookingResponse createBooking(NewBookingRequest request, Jwt jwt) {
        var service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new NotFoundException("Service not found with id: " + request.getServiceId()));

        LocalDate date;
        LocalTime time;
        try {
            date = LocalDate.parse(request.getDate());
        } catch (DateTimeParseException e) {
            throw new AppException(ErrorCode.INVALID_DATE);
        }
        try {
            time = LocalTime.parse(request.getTime());
        } catch (DateTimeParseException e) {
            throw new AppException(ErrorCode.INVALID_TIME);
        }
        LocalDateTime preferred = date.atTime(time);
        if (!preferred.isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.INVALID_DATE);
        }
        // Chặn giờ ngoài giờ mở cửa (09:00–18:00) và phút lệch bước 30' — đồng bộ với availability
        validateOpeningHours(preferred);

        validateSlot(preferred, null);

        User user = null;
        if (jwt != null && jwt.getClaim("id") != null) {
            user = userRepository.findById(UUID.fromString(jwt.getClaim("id"))).orElse(null);
        }

        Booking booking = new Booking();
        buildBooking(booking, request.getName(), null, request.getEmail(), request.getPhone(),
                service.getName(), preferred, request.getNote(), user);
        booking.setServiceId(service.getId());
        booking.setStatus(BookingStatus.IN_PROGRESS);

        bookingRepository.save(booking);
        return toResponse(booking);
    }

    /** GET /bookings — toàn bộ booking (chỉ ADMIN) */
    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingResponse> getAllBookingsResponse() {
        return bookingRepository.findAll(Sort.by(Sort.Direction.DESC, "preferredDateTime"))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** GET /bookings/my — booking của khách đang đăng nhập */
    public List<BookingResponse> getMyBookings(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaim("id"));
        return bookingRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Booking::getPreferredDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** PATCH /bookings/:id/cancel — chủ booking hoặc admin → status = "storniert" */
    @Transactional
    public BookingResponse cancelBooking(Long id, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found with id: " + id));

        String scope = jwt.getClaimAsString("scope");
        boolean isAdmin = scope != null && scope.contains("ADMIN");
        if (!isAdmin) {
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
        if (!isAdmin) {
            // Lịch đã qua giờ: khách không được cancel (admin xử lý no-show riêng)
            if (booking.getPreferredDateTime() != null
                    && booking.getPreferredDateTime().isBefore(LocalDateTime.now())) {
                throw new AppException(ErrorCode.APPOINTMENT_PASSED);
            }
            // Chính sách 24h: khách chỉ được tự hủy khi còn ít nhất 24h trước giờ hẹn (admin ngoại lệ)
            if (booking.getPreferredDateTime() != null
                    && booking.getPreferredDateTime().isBefore(LocalDateTime.now().plusHours(24))) {
                throw new AppException(ErrorCode.CANCEL_TOO_LATE);
            }
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        return toResponse(booking);
    }

    /** PATCH /bookings/:id/status — chỉ ADMIN, tuân ma trận transition chuẩn production */
    @PreAuthorize("hasRole('ADMIN')")
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
        // Lịch đã qua mà cố set lùi về trạng thái đang xử lý → cấm (giữ toàn vẹn lịch sử)
        if (BookingStatus.IN_PROGRESS.equals(request.getStatus())
                && booking.getPreferredDateTime() != null
                && booking.getPreferredDateTime().isBefore(LocalDateTime.now())
                && !BookingStatus.IN_PROGRESS.equals(current)) {
            throw new AppException(ErrorCode.APPOINTMENT_PASSED);
        }
        // Không cho đóng hoàn tất lịch chưa tới giờ (chống gian lận no-show/doanh thu ảo);
        // trường hợp ngoại lệ (khách đến sớm) admin dùng xác nhận trước rồi đóng sau khi xong.
        if (BookingStatus.COMPLETED.equals(request.getStatus())
                && booking.getPreferredDateTime() != null
                && booking.getPreferredDateTime().isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.COMPLETION_TOO_EARLY);
        }
        booking.setStatus(request.getStatus());
        if (request.getEmployee() != null && !request.getEmployee().isBlank()) {
            booking.setEmployee(request.getEmployee());
        }
        bookingRepository.save(booking);
        return toResponse(booking);
    }

    // ==================== Mapping helpers ====================

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(booking, formatPrice(resolvePrice(booking)));
    }

    private double resolvePrice(Booking booking) {
        if (booking.getServiceId() != null) {
            var service = serviceRepository.findById(booking.getServiceId());
            if (service.isPresent() && service.get().getPrice() != null) return service.get().getPrice();
        }
        if (booking.getService() != null) {
            var service = serviceRepository.findByName(booking.getService());
            if (service.isPresent() && service.get().getPrice() != null) return service.get().getPrice();
        }
        return 0;
    }

    /** "35 €" hoặc "32,50 €" */
    private String formatPrice(double price) {
        if (price == Math.floor(price) && !Double.isInfinite(price)) {
            return (long) price + " €";
        }
        return String.format(Locale.GERMAN, "%.2f €", price);
    }
}
