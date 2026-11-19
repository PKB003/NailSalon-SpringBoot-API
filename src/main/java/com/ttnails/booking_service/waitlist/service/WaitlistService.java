package com.ttnails.booking_service.waitlist.service;

import com.ttnails.booking_service.availability.AvailabilityService;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.repository.BookingItemRepository;
import com.ttnails.booking_service.bookings.service.ConfirmationCodeService;
import com.ttnails.booking_service.common.dto.PageResponse;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.common.exception.NotFoundException;
import com.ttnails.booking_service.notification.NotificationPort;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.staff.entity.Branch;
import com.ttnails.booking_service.staff.repository.BranchRepository;
import com.ttnails.booking_service.waitlist.dto.WaitlistRequest;
import com.ttnails.booking_service.waitlist.dto.WaitlistResponse;
import com.ttnails.booking_service.waitlist.entity.WaitlistEntry;
import com.ttnails.booking_service.waitlist.entity.WaitlistStatus;
import com.ttnails.booking_service.waitlist.repository.WaitlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Waitlist (S2-04).
 *
 * <p>Khách đăng ký khi hết slot; khi lịch bị huỷ/đổi, các entry ACTIVE của đúng
 * dịch vụ + ngày và thực sự có slot trống sẽ được đánh dấu NOTIFIED và gửi thông báo
 * qua {@link NotificationPort}. Unique {@code active_key} chống spam.</p>
 */
@org.springframework.stereotype.Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistRepository waitlistRepository;
    private final ServiceRepository serviceRepository;
    private final BranchRepository branchRepository;
    private final BookingItemRepository bookingItemRepository;
    private final AvailabilityService availabilityService;
    private final NotificationPort notificationPort;

    public WaitlistService(WaitlistRepository waitlistRepository,
                           ServiceRepository serviceRepository,
                           BranchRepository branchRepository,
                           BookingItemRepository bookingItemRepository,
                           AvailabilityService availabilityService,
                           NotificationPort notificationPort) {
        this.waitlistRepository = waitlistRepository;
        this.serviceRepository = serviceRepository;
        this.branchRepository = branchRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.availabilityService = availabilityService;
        this.notificationPort = notificationPort;
    }

    @Transactional
    public WaitlistResponse create(WaitlistRequest request, Jwt jwt) {
        LocalDate date;
        try {
            date = LocalDate.parse(request.getDesiredDate());
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new AppException(ErrorCode.INVALID_DATE);
        }
        if (date.isBefore(LocalDate.now())) {
            throw new AppException(ErrorCode.INVALID_DATE);
        }
        LocalTime time = null;
        if (request.getDesiredStartTime() != null && !request.getDesiredStartTime().isBlank()) {
            try {
                time = LocalTime.parse(request.getDesiredStartTime());
            } catch (DateTimeParseException ex) {
                throw new AppException(ErrorCode.INVALID_TIME);
            }
        }
        Service service = serviceRepository.findByIdAndActiveTrue(request.getServiceId())
                .orElseThrow(() -> new NotFoundException("Service not found or inactive with id: " + request.getServiceId()));
        Branch branch = branchRepository.findFirstByActiveTrueOrderByIdAsc().orElse(null);

        String phone = ConfirmationCodeService.normalizePhone(request.getPhone());
        String activeKey = phone + "|" + service.getId() + "|" + date;
        if (waitlistRepository.findByActiveKey(activeKey).isPresent()) {
            throw new AppException(ErrorCode.WAITLIST_DUPLICATE);
        }

        String customerId = null;
        if (jwt != null && jwt.getClaim("id") != null) {
            customerId = jwt.getClaimAsString("id");
        }

        WaitlistEntry entry = WaitlistEntry.builder()
                .branchId(branch != null ? branch.getId() : null)
                .serviceId(service.getId())
                .desiredDate(date)
                .desiredStartTime(time)
                .customerId(customerId)
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .note(request.getNote())
                .status(WaitlistStatus.ACTIVE)
                .activeKey(activeKey)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            entry = waitlistRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.WAITLIST_DUPLICATE);
        }
        return toResponse(entry);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional(readOnly = true)
    public PageResponse<WaitlistResponse> list(LocalDate date, Long serviceId, String status,
                                               int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<WaitlistEntry> result;
        if (date != null && serviceId != null && status != null && !status.isBlank()) {
            result = waitlistRepository.findByDesiredDateAndServiceIdAndStatus(date, serviceId, status, pageable);
        } else if (date != null && serviceId != null) {
            result = waitlistRepository.findByDesiredDateAndServiceId(date, serviceId, pageable);
        } else {
            result = waitlistRepository.findAll(pageable);
        }
        return PageResponse.from(result.map(this::toResponse));
    }

    /** Khách huỷ đăng ký (xác thực bằng SĐT) hoặc staff huỷ hộ. */
    @Transactional
    public void cancel(Long id, String phone, Jwt jwt) {
        WaitlistEntry entry = waitlistRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Waitlist entry not found with id: " + id));
        boolean staff = hasAnyRole(jwt, "ADMIN", "MANAGER", "RECEPTIONIST");
        if (!staff) {
            if (phone == null || !ConfirmationCodeService.phoneMatches(entry.getPhone(), phone)) {
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
        }
        if (WaitlistStatus.CANCELLED.equals(entry.getStatus())) {
            return;
        }
        entry.setStatus(WaitlistStatus.CANCELLED);
        entry.setActiveKey(null);
        waitlistRepository.save(entry);
    }

    /**
     * Gọi khi một booking bị huỷ/đổi lịch (slot được giải phóng).
     *
     * @return số entry đã được thông báo
     */
    @Transactional
    public int notifyReleasedSlot(Booking booking) {
        if (booking.getStartAt() == null) return 0;
        Set<Long> serviceIds = new LinkedHashSet<>();
        if (booking.getServiceId() != null) serviceIds.add(booking.getServiceId());
        bookingItemRepository.findByBookingIdOrderByStartAtAsc(booking.getId())
                .forEach(item -> serviceIds.add(item.getService().getId()));
        return notifyReleasedSlot(booking.getStartAt().toLocalDate(), serviceIds);
    }

    /**
     * S2-02/S2-04: gọi khi slot của một ngày/dịch vụ được giải phóng (huỷ hoặc đổi lịch).
     * Chỉ thông báo khi availability thực sự còn slot trống.
     */
    @Transactional
    public int notifyReleasedSlot(LocalDate date, Collection<Long> serviceIds) {
        if (date == null || serviceIds == null || serviceIds.isEmpty()) return 0;

        int notified = 0;
        for (Long serviceId : serviceIds) {
            Service service = serviceRepository.findByIdAndActiveTrue(serviceId).orElse(null);
            if (service == null) continue;
            if (availabilityService.computeDayForServices(date, List.of(service), null)
                    .result().anyAvailable().isEmpty()) {
                continue;
            }
            List<WaitlistEntry> entries = waitlistRepository
                    .findByServiceIdAndDesiredDateAndStatusOrderByCreatedAtAsc(
                            serviceId, date, WaitlistStatus.ACTIVE);
            for (WaitlistEntry entry : entries) {
                entry.setStatus(WaitlistStatus.NOTIFIED);
                entry.setNotifiedAt(LocalDateTime.now());
                entry.setActiveKey(null);
                waitlistRepository.save(entry);
                try {
                    notificationPort.waitlistSlotFreed(entry.getPhone(), entry.getName(), serviceId, date,
                            "Termin am " + date + " frei geworden");
                } catch (RuntimeException ex) {
                    log.warn("Cannot notify waitlist entry {}: {}", entry.getId(), ex.getMessage());
                }
                notified++;
            }
        }
        return notified;
    }

    private WaitlistResponse toResponse(WaitlistEntry entry) {
        WaitlistResponse response = WaitlistResponse.from(entry);
        serviceRepository.findById(entry.getServiceId())
                .map(Service::getName)
                .ifPresent(response::setServiceName);
        return response;
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
}
