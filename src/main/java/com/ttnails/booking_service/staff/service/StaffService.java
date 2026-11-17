package com.ttnails.booking_service.staff.service;

import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.bookings.service.BookingMapper;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.common.exception.NotFoundException;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.staff.dto.ShiftDTO;
import com.ttnails.booking_service.staff.dto.ShiftRequest;
import com.ttnails.booking_service.staff.dto.SkillRequest;
import com.ttnails.booking_service.staff.dto.StaffDTO;
import com.ttnails.booking_service.staff.dto.StaffRequest;
import com.ttnails.booking_service.staff.dto.TimeOffDTO;
import com.ttnails.booking_service.staff.dto.TimeOffRequest;
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
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * S1-02 — CRUD thợ, ca làm và nghỉ phép.
 *
 * <p>Quyền: ADMIN/MANAGER ghi; RECEPTIONIST đọc; NAIL_TECH chỉ đọc lịch của chính mình.
 * “Chính mình” xác định qua {@code staff.user_id} = id trong JWT.</p>
 */
@org.springframework.stereotype.Service
public class StaffService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> TERMINAL_BOOKING_STATUSES =
            List.of(com.ttnails.booking_service.common.constant.BookingStatus.CANCELLED,
                    com.ttnails.booking_service.common.constant.BookingStatus.COMPLETED);

    private final StaffRepository staffRepository;
    private final StaffSkillRepository staffSkillRepository;
    private final ShiftRepository shiftRepository;
    private final TimeOffRepository timeOffRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;

    public StaffService(StaffRepository staffRepository,
                        StaffSkillRepository staffSkillRepository,
                        ShiftRepository shiftRepository,
                        TimeOffRepository timeOffRepository,
                        BranchRepository branchRepository,
                        ServiceRepository serviceRepository,
                        UserRepository userRepository,
                        BookingRepository bookingRepository,
                        BookingMapper bookingMapper) {
        this.staffRepository = staffRepository;
        this.staffSkillRepository = staffSkillRepository;
        this.shiftRepository = shiftRepository;
        this.timeOffRepository = timeOffRepository;
        this.branchRepository = branchRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
    }

    // ==================== Staff ====================

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<StaffDTO> list(boolean includeInactive) {
        List<Staff> staff = includeInactive
                ? staffRepository.findAll()
                : staffRepository.findByActiveTrueOrderByIdAsc();
        Map<Long, List<StaffSkill>> skills = staffSkillRepository
                .findByStaffIdIn(staff.stream().map(Staff::getId).toList()).stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId()));
        return staff.stream()
                .sorted(Comparator.comparing(Staff::getId))
                .map(s -> toDTO(s, skills.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional(readOnly = true)
    public StaffDTO get(Long id) {
        Staff staff = requireStaff(id);
        return toDTO(staff, staffSkillRepository.findByStaffId(id));
    }

    /** GET /staff/me — thợ xem hồ sơ của mình. */
    @Transactional(readOnly = true)
    public StaffDTO getMe(Jwt jwt) {
        Staff self = requireSelf(jwt);
        return toDTO(self, staffSkillRepository.findByStaffId(self.getId()));
    }

    /**
     * PUT /staff/{id}/skills — thay toàn bộ danh sách kỹ năng (ADMIN/MANAGER).
     * Trả về danh sách kỹ năng sau khi cập nhật.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public List<StaffDTO.Skill> replaceSkills(Long id, List<SkillRequest> skills) {
        Staff staff = requireStaff(id);
        replaceSkills(staff, skills);
        return toDTO(staff, staffSkillRepository.findByStaffId(staff.getId())).getSkills();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public StaffDTO create(StaffRequest request) {
        Branch branch = resolveBranch(request.getBranchId());
        User user = resolveUser(request.getUserId(), null);

        Staff staff = Staff.builder()
                .user(user)
                .branch(branch)
                .displayName(request.getDisplayName().trim())
                .color(request.getColor())
                .active(request.getActive() == null || request.getActive())
                .commissionRate(request.getCommissionRate() != null
                        ? request.getCommissionRate() : new BigDecimal("0.4000"))
                .build();
        staff = staffRepository.save(staff);
        replaceSkills(staff, request.getSkills());
        return toDTO(staff, staffSkillRepository.findByStaffId(staff.getId()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public StaffDTO update(Long id, StaffRequest request) {
        Staff staff = requireStaff(id);

        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            staff.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getColor() != null) staff.setColor(request.getColor());
        if (request.getActive() != null) staff.setActive(request.getActive());
        if (request.getCommissionRate() != null) staff.setCommissionRate(request.getCommissionRate());
        if (request.getBranchId() != null) staff.setBranch(resolveBranch(request.getBranchId()));
        if (request.getUserId() != null
                && (staff.getUser() == null || !staff.getUser().getId().equals(request.getUserId()))) {
            staff.setUser(resolveUser(request.getUserId(), id));
        }

        staff = staffRepository.save(staff);
        if (request.getSkills() != null) {
            replaceSkills(staff, request.getSkills());
        }
        return toDTO(staff, staffSkillRepository.findByStaffId(staff.getId()));
    }

    /** Soft delete: giữ lịch sử booking/commission, chỉ tắt nhận khách. */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public void deactivate(Long id) {
        Staff staff = requireStaff(id);
        staff.setActive(false);
        staffRepository.save(staff);
    }

    // ==================== Shifts ====================

    /** Danh sách ca của thợ — ADMIN/MANAGER/RECEPTIONIST hoặc chính thợ đó. */
    @Transactional(readOnly = true)
    public List<ShiftDTO> listShifts(Long staffId, LocalDate from, LocalDate to, Jwt jwt) {
        requireSelfOrPrivileged(staffId, jwt);
        LocalDate safeFrom = from != null ? from : LocalDate.now();
        LocalDate safeTo = to != null ? to : safeFrom.plusDays(13);
        if (safeTo.isBefore(safeFrom)) {
            throw new AppException(ErrorCode.INVALID_DATE);
        }
        return shiftRepository
                .findByStaffIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(staffId, safeFrom, safeTo)
                .stream().map(this::toDTO).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public ShiftDTO addShift(Long staffId, ShiftRequest request) {
        Staff staff = requireStaff(staffId);
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new AppException(ErrorCode.INVALID_TIME_RANGE);
        }
        boolean overlaps = shiftRepository
                .existsByStaffIdAndWorkDateAndStartTimeLessThanAndEndTimeGreaterThan(
                        staffId, request.getWorkDate(), request.getEndTime(), request.getStartTime());
        if (overlaps) {
            throw new AppException(ErrorCode.SHIFT_OVERLAP);
        }
        Shift shift = Shift.builder()
                .staff(staff)
                .workDate(request.getWorkDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .build();
        return toDTO(shiftRepository.save(shift));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public void deleteShift(Long staffId, Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new NotFoundException("Shift not found with id: " + shiftId));
        if (!shift.getStaff().getId().equals(staffId)) {
            throw new NotFoundException("Shift " + shiftId + " does not belong to staff " + staffId);
        }
        shiftRepository.delete(shift);
    }

    // ==================== Time off ====================

    @Transactional(readOnly = true)
    public List<TimeOffDTO> listTimeOff(Long staffId, Jwt jwt) {
        requireSelfOrPrivileged(staffId, jwt);
        return timeOffRepository.findByStaffIdOrderByStartAtAsc(staffId).stream()
                .map(this::toDTO).toList();
    }

    /** Thợ tự đăng ký nghỉ cho mình, hoặc ADMIN/MANAGER đăng ký hộ. */
    @Transactional
    public TimeOffDTO addTimeOff(Long staffId, TimeOffRequest request, Jwt jwt) {
        requireSelfOrPrivileged(staffId, jwt);
        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new AppException(ErrorCode.INVALID_TIME_RANGE);
        }
        Staff staff = requireStaff(staffId);
        boolean overlaps = timeOffRepository
                .existsByStaffIdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
                        staffId, TimeOffStatus.ACTIVE, request.getEndAt(), request.getStartAt());
        if (overlaps) {
            throw new AppException(ErrorCode.TIME_OFF_OVERLAP);
        }
        long conflicts = bookingRepository.countOverlapping(staffId, -1L,
                request.getStartAt(), request.getEndAt(), TERMINAL_BOOKING_STATUSES);
        if (conflicts > 0) {
            throw new AppException(ErrorCode.TIME_OFF_CONFLICTS_BOOKING);
        }
        TimeOff timeOff = TimeOff.builder()
                .staff(staff)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .reason(request.getReason())
                .status(TimeOffStatus.ACTIVE)
                .build();
        return toDTO(timeOffRepository.save(timeOff));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public void cancelTimeOff(Long staffId, Long timeOffId) {
        TimeOff timeOff = timeOffRepository.findById(timeOffId)
                .orElseThrow(() -> new NotFoundException("Time off not found with id: " + timeOffId));
        if (!timeOff.getStaff().getId().equals(staffId)) {
            throw new NotFoundException("Time off " + timeOffId + " does not belong to staff " + staffId);
        }
        timeOff.setStatus(TimeOffStatus.CANCELLED);
        timeOffRepository.save(timeOff);
    }

    // ==================== My bookings (NAIL_TECH) ====================

    /** GET /staff/me/bookings — thợ chỉ thấy lịch của mình (S1-07). */
    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(Jwt jwt) {
        Staff staff = requireSelf(jwt);
        return bookingRepository.findByStaffIdOrderByStartAtDesc(staff.getId()).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> bookingsOfStaff(Long staffId, Jwt jwt) {
        requireSelfOrPrivileged(staffId, jwt);
        return bookingRepository.findByStaffIdOrderByStartAtDesc(staffId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    // ==================== Helpers ====================

    private Staff requireStaff(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Staff not found with id: " + id));
    }

    private Staff requireSelf(Jwt jwt) {
        UUID userId = currentUserId(jwt);
        return staffRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No staff profile linked to current user"));
    }

    private void requireSelfOrPrivileged(Long staffId, Jwt jwt) {
        if (isPrivileged(jwt)) return;
        Staff self = requireSelf(jwt);
        if (!self.getId().equals(staffId)) {
            boolean isAdmin = roles(jwt).contains("ADMIN");
            if (!isAdmin) throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private UUID currentUserId(Jwt jwt) {
        String id = jwt.getClaimAsString("id");
        if (id == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        return UUID.fromString(id);
    }

    private Set<String> roles(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) return Set.of();
        return new HashSet<>(List.of(scope.split(" ")));
    }

    private boolean isPrivileged(Jwt jwt) {
        Set<String> roles = roles(jwt);
        return roles.contains("ADMIN") || roles.contains("MANAGER") || roles.contains("RECEPTIONIST");
    }

    private Branch resolveBranch(Long branchId) {
        if (branchId != null) {
            return branchRepository.findById(branchId)
                    .orElseThrow(() -> new NotFoundException("Branch not found with id: " + branchId));
        }
        return branchRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("No active branch configured"));
    }

    private User resolveUser(UUID userId, Long staffId) {
        if (userId == null) return null;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        staffRepository.findByUserId(userId).ifPresent(existing -> {
            if (staffId == null || !existing.getId().equals(staffId)) {
                throw new AppException(ErrorCode.STAFF_USER_ALREADY_LINKED);
            }
        });
        return user;
    }

    private void replaceSkills(Staff staff, List<SkillRequest> skills) {
        if (skills == null) return;
        List<SkillRequest> distinct = skills.stream()
                .filter(s -> s != null && s.serviceId() != null)
                .collect(Collectors.toMap(SkillRequest::serviceId, Function.identity(), (a, b) -> b))
                .values().stream().toList();

        Map<Long, Service> services = serviceRepository.findAllById(
                        distinct.stream().map(SkillRequest::serviceId).toList()).stream()
                .collect(Collectors.toMap(Service::getId, Function.identity()));
        for (SkillRequest skill : distinct) {
            if (!services.containsKey(skill.serviceId())) {
                throw new NotFoundException("Service not found with id: " + skill.serviceId());
            }
        }

        staffSkillRepository.deleteByStaffId(staff.getId());
        List<StaffSkill> entities = new ArrayList<>();
        for (SkillRequest skill : distinct) {
            entities.add(StaffSkill.builder()
                    .staff(staff)
                    .service(services.get(skill.serviceId()))
                    .level(skill.levelOrDefault())
                    .build());
        }
        staffSkillRepository.saveAll(entities);
    }

    private StaffDTO toDTO(Staff staff, List<StaffSkill> skills) {
        List<StaffDTO.Skill> skillDtos = skills.stream()
                .sorted(Comparator.comparing(s -> s.getService().getId()))
                .map(s -> new StaffDTO.Skill(
                        s.getService().getId(),
                        s.getService().getName(),
                        s.getService().getDuration(),
                        s.getService().getPrice(),
                        s.getLevel()))
                .toList();
        return new StaffDTO(
                staff.getId(),
                staff.getDisplayName(),
                staff.getColor(),
                staff.isActive(),
                staff.getCommissionRate(),
                staff.getBranch() != null ? staff.getBranch().getId() : null,
                staff.getUser() != null ? staff.getUser().getId() : null,
                skillDtos);
    }

    private ShiftDTO toDTO(Shift shift) {
        return new ShiftDTO(
                shift.getId(),
                shift.getStaff().getId(),
                shift.getWorkDate(),
                shift.getStartTime().format(HH_MM),
                shift.getEndTime().format(HH_MM));
    }

    private TimeOffDTO toDTO(TimeOff timeOff) {
        return new TimeOffDTO(
                timeOff.getId(),
                timeOff.getStaff().getId(),
                timeOff.getStartAt(),
                timeOff.getEndAt(),
                timeOff.getReason(),
                timeOff.getStatus());
    }

    /** Dùng cho seed/test: tạo nhanh ca làm nếu chưa có. */
    @Transactional
    public void ensureShift(Staff staff, LocalDate date, java.time.LocalTime start, java.time.LocalTime end) {
        boolean exists = shiftRepository.existsByStaffIdAndWorkDateAndStartTimeLessThanAndEndTimeGreaterThan(
                staff.getId(), date, end, start);
        if (!exists) {
            shiftRepository.save(Shift.builder()
                    .staff(staff).workDate(date).startTime(start).endTime(end).build());
        }
    }

    /** Dùng cho seed/test: gán kỹ năng cho mọi dịch vụ đang active. */
    @Transactional
    public void grantAllSkills(Staff staff) {
        List<StaffSkill> existing = staffSkillRepository.findByStaffId(staff.getId());
        Set<Long> have = existing.stream().map(s -> s.getService().getId()).collect(Collectors.toSet());
        List<StaffSkill> toSave = serviceRepository.findByActiveTrueOrderByIdAsc().stream()
                .filter(s -> !have.contains(s.getId()))
                .map(s -> StaffSkill.builder()
                        .staff(staff)
                        .service(s)
                        .level(com.ttnails.booking_service.common.enums.SkillLevel.ADVANCED)
                        .build())
                .toList();
        staffSkillRepository.saveAll(toSave);
    }

    /** Tiện cho test: số booking chưa terminal của thợ. */
    @Transactional(readOnly = true)
    public long countActiveBookings(Long staffId) {
        return bookingRepository.findByStaffIdOrderByStartAtDesc(staffId).stream()
                .filter((Booking b) -> !TERMINAL_BOOKING_STATUSES.contains(b.getStatus()))
                .count();
    }

    private LocalDateTime at(LocalDate date, java.time.LocalTime time) {
        return date.atTime(time);
    }
}
