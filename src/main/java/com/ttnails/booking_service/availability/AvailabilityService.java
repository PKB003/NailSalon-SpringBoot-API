package com.ttnails.booking_service.availability;

import com.ttnails.booking_service.availability.dto.AvailabilityResponse;
import com.ttnails.booking_service.availability.dto.SlotDTO;
import com.ttnails.booking_service.availability.dto.StaffSlotsDTO;
import com.ttnails.booking_service.availability.engine.AvailabilityEngine;
import com.ttnails.booking_service.availability.engine.AvailabilityQuery;
import com.ttnails.booking_service.availability.engine.AvailabilityResult;
import com.ttnails.booking_service.availability.engine.Interval;
import com.ttnails.booking_service.availability.engine.ServiceSpec;
import com.ttnails.booking_service.availability.engine.SlotOption;
import com.ttnails.booking_service.availability.engine.StaffSchedule;
import com.ttnails.booking_service.availability.engine.StaffSlots;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.common.exception.NotFoundException;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.staff.entity.Branch;
import com.ttnails.booking_service.staff.entity.Shift;
import com.ttnails.booking_service.staff.entity.Staff;
import com.ttnails.booking_service.staff.entity.StaffSkill;
import com.ttnails.booking_service.staff.entity.TimeOff;
import com.ttnails.booking_service.staff.entity.TimeOffStatus;
import com.ttnails.booking_service.staff.repository.BranchRepository;
import com.ttnails.booking_service.staff.repository.ShiftRepository;
import com.ttnails.booking_service.staff.repository.StaffRepository;
import com.ttnails.booking_service.staff.repository.StaffSkillRepository;
import com.ttnails.booking_service.staff.repository.TimeOffRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ghép DB → {@link AvailabilityEngine} (S1-03) và cung cấp dữ liệu cho booking (S1-05).
 *
 * <p>Không cache: dữ liệu lịch thay đổi liên tục; tối ưu index ở V2 là đủ cho MVP.</p>
 */
@org.springframework.stereotype.Service
public class AvailabilityService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> TERMINAL_STATUSES =
            List.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED);

    private final AvailabilityEngine engine = new AvailabilityEngine();

    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final StaffSkillRepository staffSkillRepository;
    private final ShiftRepository shiftRepository;
    private final TimeOffRepository timeOffRepository;
    private final BookingRepository bookingRepository;
    private final ServiceRepository serviceRepository;

    @Value("${app.booking.min-lead-minutes:30}")
    private int minLeadMinutes;

    @Value("${app.booking.max-services:3}")
    private int maxServices;

    public AvailabilityService(BranchRepository branchRepository,
                               StaffRepository staffRepository,
                               StaffSkillRepository staffSkillRepository,
                               ShiftRepository shiftRepository,
                               TimeOffRepository timeOffRepository,
                               BookingRepository bookingRepository,
                               ServiceRepository serviceRepository) {
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.staffSkillRepository = staffSkillRepository;
        this.shiftRepository = shiftRepository;
        this.timeOffRepository = timeOffRepository;
        this.bookingRepository = bookingRepository;
        this.serviceRepository = serviceRepository;
    }

    /** Dữ liệu availability của một ngày + thông tin phụ để chọn thợ khi tạo booking. */
    public record DayAvailability(AvailabilityResult result,
                                  Branch branch,
                                  Map<Long, Staff> staffById,
                                  Map<Long, Long> bookedMinutes) {

        public boolean hasSlotAt(LocalDateTime start) {
            return result.anyAvailable().contains(start);
        }
    }

    /** GET /availability — serviceIds theo thứ tự khách chọn (1..3). */
    @Transactional(readOnly = true)
    public DayAvailability computeDay(LocalDate date, List<Long> serviceIds, Long requestedStaffId) {
        List<Long> ids = serviceIds == null ? List.of() : serviceIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (ids.size() > maxServices) {
            throw new AppException(ErrorCode.TOO_MANY_SERVICES);
        }

        Map<Long, Service> byId = serviceRepository.findByIdInAndActiveTrue(ids).stream()
                .collect(Collectors.toMap(Service::getId, Function.identity()));
        List<Service> ordered = new ArrayList<>();
        for (Long id : ids) {
            Service service = byId.get(id);
            if (service == null) {
                throw new NotFoundException("Service not found or inactive with id: " + id);
            }
            ordered.add(service);
        }
        return computeDayForServices(date, ordered, requestedStaffId);
    }

    /** Dùng nội bộ cho BookingService (đã resolve entity service). */
    @Transactional(readOnly = true)
    public DayAvailability computeDayForServices(LocalDate date, List<Service> services, Long requestedStaffId) {
        if (services == null || services.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (services.size() > maxServices) {
            throw new AppException(ErrorCode.TOO_MANY_SERVICES);
        }

        Branch branch = branchRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("No active branch configured"));

        List<Staff> staff = resolveStaff(branch, requestedStaffId);
        if (staff.isEmpty()) {
            return emptyDay(date, branch, services);
        }

        List<Long> staffIds = staff.stream().map(Staff::getId).toList();
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        Map<Long, Set<Long>> skillsByStaff = staffSkillRepository.findByStaffIdIn(staffIds).stream()
                .collect(Collectors.groupingBy(ss -> ss.getStaff().getId(),
                        Collectors.mapping(ss -> ss.getService().getId(), Collectors.toSet())));

        Map<Long, List<Shift>> shiftsByStaff = shiftRepository
                .findByStaffIdInAndWorkDate(staffIds, date).stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId()));

        Map<Long, List<TimeOff>> timeOffByStaff = timeOffRepository
                .findByStaffIdInAndStatusAndStartAtLessThanAndEndAtGreaterThan(
                        staffIds, TimeOffStatus.ACTIVE, dayEnd, dayStart)
                .stream().collect(Collectors.groupingBy(t -> t.getStaff().getId()));

        List<Booking> blocking = bookingRepository.findBlockingForStaff(
                staffIds, dayStart, dayEnd, TERMINAL_STATUSES);
        Map<Long, List<Booking>> bookingsByStaff = blocking.stream()
                .collect(Collectors.groupingBy(b -> b.getStaff().getId()));

        List<StaffSchedule> schedules = new ArrayList<>();
        Map<Long, Long> bookedMinutes = new HashMap<>();
        for (Staff s : staff) {
            List<Interval> shifts = shiftsByStaff.getOrDefault(s.getId(), List.of()).stream()
                    .map(sh -> intervalFor(date, sh))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            List<Interval> offs = timeOffByStaff.getOrDefault(s.getId(), List.of()).stream()
                    .map(t -> new Interval(t.getStartAt(), t.getEndAt()))
                    .toList();
            List<Interval> busy = bookingsByStaff.getOrDefault(s.getId(), List.of()).stream()
                    .map(b -> new Interval(b.getStartAt(), b.resolveBufferEndAt()))
                    .toList();
            schedules.add(new StaffSchedule(s.getId(), s.getDisplayName(),
                    skillsByStaff.getOrDefault(s.getId(), Set.of()), shifts, offs, busy));
            bookedMinutes.put(s.getId(), busy.stream().mapToLong(Interval::minutes).sum());
        }

        ZoneId zone = ZoneId.of(branch.getTimezone());
        LocalDateTime now = LocalDateTime.now(zone);
        List<ServiceSpec> specs = services.stream()
                .map(sv -> new ServiceSpec(sv.getId(), sv.getName(),
                        sv.effectiveDuration(), sv.effectiveBuffer(),
                        sv.getPrice() != null ? BigDecimal.valueOf(sv.getPrice()) : BigDecimal.ZERO))
                .toList();

        AvailabilityResult result = engine.compute(new AvailabilityQuery(
                date, zone, now, branch.getOpenTime(), branch.getCloseTime(),
                branch.getSlotStepMinutes(), minLeadMinutes, specs, schedules, requestedStaffId));

        Map<Long, Staff> staffById = staff.stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return new DayAvailability(result, branch, staffById, bookedMinutes);
    }

    /**
     * Chọn thợ cho một slot cụ thể (S1-05): ưu tiên thợ ít việc nhất trong ngày,
     * tie-break theo staffId. Trả {@code null} nếu không thợ nào rảnh.
     */
    public Long pickStaff(DayAvailability day, LocalDateTime start, Long requestedStaffId) {
        List<Long> candidates = day.result().staff().stream()
                .filter(s -> s.slots().stream().anyMatch(slot -> slot.start().equals(start)))
                .map(StaffSlots::staffId)
                .filter(id -> requestedStaffId == null || requestedStaffId.equals(id))
                .sorted(Comparator
                        .comparingLong((Long id) -> day.bookedMinutes().getOrDefault(id, 0L))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** Danh sách thợ rảnh tại slot, đã sắp xếp theo tải (dùng để lock lần lượt khi đặt). */
    public List<Long> candidateStaffForSlot(DayAvailability day, LocalDateTime start, Long requestedStaffId) {
        return day.result().staff().stream()
                .filter(s -> s.slots().stream().anyMatch(slot -> slot.start().equals(start)))
                .map(StaffSlots::staffId)
                .filter(id -> requestedStaffId == null || requestedStaffId.equals(id))
                .sorted(Comparator
                        .comparingLong((Long id) -> day.bookedMinutes().getOrDefault(id, 0L))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** Map kết quả engine → DTO của API. */
    public AvailabilityResponse toResponse(DayAvailability day) {
        List<StaffSlotsDTO> staff = day.result().staff().stream()
                .map(s -> new StaffSlotsDTO(s.staffId(), s.displayName(),
                        s.slots().stream()
                                .map(slot -> new SlotDTO(format(slot.start()), format(slot.end())))
                                .toList()))
                .toList();
        List<String> anyAvailable = day.result().anyAvailable().stream()
                .map(this::format)
                .toList();
        return new AvailabilityResponse(
                day.result().date().toString(),
                day.branch().getTimezone(),
                day.result().slotStepMinutes(),
                day.result().totalDurationMinutes(),
                staff,
                anyAvailable);
    }

    /**
     * Endpoint legacy {@code GET /bookings/availability?date=} (deprecated):
     * trả giờ bắt đầu còn trống theo dịch vụ ngắn nhất đang bán.
     */
    @Transactional(readOnly = true)
    public List<String> legacySlots(LocalDate date) {
        return serviceRepository.findFirstByActiveTrueOrderByDurationAsc()
                .map(service -> computeDayForServices(date, List.of(service), null)
                        .result().anyAvailable().stream().map(this::format).toList())
                .orElseGet(List::of);
    }

    /** Slot khả dụng của một thợ trong khoảng ngày (dùng cho thợ xem lịch mình). */
    public List<String> availableStartsForStaff(LocalDate date, List<Long> serviceIds, Long staffId) {
        return computeDay(date, serviceIds, staffId).result().anyAvailable().stream()
                .map(this::format)
                .toList();
    }

    /**
     * Thợ có đủ skill + có ca làm trong ngày hay không — dùng để phân biệt
     * {@code SLOT_TAKEN} (thợ làm được nhưng kín giờ) với {@code NO_STAFF_AVAILABLE}
     * (không ai làm được / ngày nghỉ).
     */
    @Transactional(readOnly = true)
    public boolean canStaffWorkOn(Long staffId, List<Long> serviceIds, LocalDate date) {
        if (serviceIds == null || serviceIds.isEmpty()) return false;
        if (shiftRepository.findByStaffIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                staffId, date, date).isEmpty()) {
            return false;
        }
        Set<Long> have = staffSkillRepository.findByStaffId(staffId).stream()
                .map(ss -> ss.getService().getId())
                .collect(Collectors.toSet());
        return have.containsAll(serviceIds);
    }

    /** Có ít nhất một thợ trong branch đủ skill + có ca trong ngày hay không. */
    @Transactional(readOnly = true)
    public boolean anyStaffCanWorkOn(Long branchId, List<Long> serviceIds, LocalDate date) {
        if (serviceIds == null || serviceIds.isEmpty()) return false;
        List<Long> staffIds = staffRepository.findByBranchIdAndActiveTrueOrderByIdAsc(branchId).stream()
                .map(Staff::getId)
                .toList();
        if (staffIds.isEmpty()) return false;

        Set<Long> staffWithShift = shiftRepository.findByStaffIdInAndWorkDate(staffIds, date).stream()
                .map(sh -> sh.getStaff().getId())
                .collect(Collectors.toSet());
        if (staffWithShift.isEmpty()) return false;

        return staffSkillRepository.findByStaffIdIn(staffIds).stream()
                .collect(Collectors.groupingBy(ss -> ss.getStaff().getId(),
                        Collectors.mapping(ss -> ss.getService().getId(), Collectors.toSet())))
                .entrySet().stream()
                .filter(e -> staffWithShift.contains(e.getKey()))
                .anyMatch(e -> e.getValue().containsAll(serviceIds));
    }

    private List<Staff> resolveStaff(Branch branch, Long requestedStaffId) {
        if (requestedStaffId != null) {
            Staff staff = staffRepository.findById(requestedStaffId)
                    .orElseThrow(() -> new NotFoundException("Staff not found with id: " + requestedStaffId));
            if (!staff.isActive() || !staff.getBranch().getId().equals(branch.getId())) {
                throw new NotFoundException("Staff not available with id: " + requestedStaffId);
            }
            return List.of(staff);
        }
        return staffRepository.findByBranchIdAndActiveTrueOrderByIdAsc(branch.getId());
    }

    private DayAvailability emptyDay(LocalDate date, Branch branch, List<Service> services) {
        int duration = services.stream().mapToInt(Service::effectiveDuration).sum();
        return new DayAvailability(
                new AvailabilityResult(date, ZoneId.of(branch.getTimezone()),
                        branch.getSlotStepMinutes(), duration, List.of(), List.of()),
                branch, Map.of(), Map.of());
    }

    private Interval intervalFor(LocalDate date, Shift shift) {
        if (shift.getStartTime() == null || shift.getEndTime() == null) return null;
        if (!shift.getEndTime().isAfter(shift.getStartTime())) return null;
        return new Interval(date.atTime(shift.getStartTime()), date.atTime(shift.getEndTime()));
    }

    private String format(LocalDateTime dateTime) {
        return dateTime.toLocalTime().format(HH_MM);
    }

    /** Helper cho test/khác: union giờ bắt đầu. */
    public static List<LocalTime> starts(AvailabilityResult result) {
        return result.anyAvailable().stream().map(LocalDateTime::toLocalTime).toList();
    }

    /** Helper: danh sách staffId có slot tại thời điểm. */
    public static Collection<Long> staffAt(AvailabilityResult result, LocalDateTime start) {
        return result.staff().stream()
                .filter(s -> s.slots().stream().map(SlotOption::start).anyMatch(start::equals))
                .map(StaffSlots::staffId)
                .toList();
    }
}
