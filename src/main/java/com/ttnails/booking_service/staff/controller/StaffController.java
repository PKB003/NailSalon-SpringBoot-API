package com.ttnails.booking_service.staff.controller;

import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.staff.dto.ShiftDTO;
import com.ttnails.booking_service.staff.dto.ShiftRequest;
import com.ttnails.booking_service.staff.dto.SkillRequest;
import com.ttnails.booking_service.staff.dto.StaffDTO;
import com.ttnails.booking_service.staff.dto.StaffRequest;
import com.ttnails.booking_service.staff.dto.TimeOffDTO;
import com.ttnails.booking_service.staff.dto.TimeOffRequest;
import com.ttnails.booking_service.staff.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * S1-02 — API quản lý thợ, kỹ năng, ca làm và nghỉ phép.
 *
 * <p>Phân quyền (S1-07):</p>
 * <ul>
 *   <li>{@code ADMIN}/{@code MANAGER}: toàn quyền.</li>
 *   <li>{@code RECEPTIONIST}: chỉ đọc.</li>
 *   <li>{@code NAIL_TECH}: chỉ {@code /staff/me/**} (lịch của chính mình).</li>
 * </ul>
 *
 * <p>Lưu ý: các route literal ({@code /staff/me}) được Spring ưu tiên hơn {@code /staff/{id}}.</p>
 */
@RestController
@RequestMapping("/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    // ==================== Staff ====================

    /** GET /staff?includeInactive=false — danh sách thợ (ADMIN/MANAGER/RECEPTIONIST). */
    @GetMapping
    public ApiResponse<List<StaffDTO>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(200, "Success", staffService.list(includeInactive));
    }

    /** GET /staff/me — hồ sơ thợ gắn với tài khoản đang đăng nhập. */
    @GetMapping("/me")
    public ApiResponse<StaffDTO> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(200, "Success", staffService.getMe(jwt));
    }

    /** GET /staff/me/bookings — lịch của chính thợ (S1-07: thợ chỉ thấy lịch mình). */
    @GetMapping("/me/bookings")
    public ApiResponse<List<BookingResponse>> myBookings(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(200, "Success", staffService.myBookings(jwt));
    }

    /** GET /staff/me/shifts?from=&to= — ca làm của chính thợ. */
    @GetMapping("/me/shifts")
    public ApiResponse<List<ShiftDTO>> myShifts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        Long staffId = staffService.getMe(jwt).getId();
        return ApiResponse.success(200, "Success", staffService.listShifts(staffId, from, to, jwt));
    }

    /** GET /staff/me/time-off — nghỉ phép của chính thợ. */
    @GetMapping("/me/time-off")
    public ApiResponse<List<TimeOffDTO>> myTimeOff(@AuthenticationPrincipal Jwt jwt) {
        Long staffId = staffService.getMe(jwt).getId();
        return ApiResponse.success(200, "Success", staffService.listTimeOff(staffId, jwt));
    }

    /** POST /staff/me/time-off — thợ tự đăng ký nghỉ. */
    @PostMapping("/me/time-off")
    public ApiResponse<TimeOffDTO> requestMyTimeOff(@RequestBody @Valid TimeOffRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        Long staffId = staffService.getMe(jwt).getId();
        return ApiResponse.success(201, "Time off created",
                staffService.addTimeOff(staffId, request, jwt));
    }

    /** GET /staff/{id} — chi tiết thợ. */
    @GetMapping("/{id}")
    public ApiResponse<StaffDTO> get(@PathVariable Long id) {
        return ApiResponse.success(200, "Success", staffService.get(id));
    }

    /** POST /staff — tạo thợ (ADMIN/MANAGER). */
    @PostMapping
    public ApiResponse<StaffDTO> create(@RequestBody @Valid StaffRequest request) {
        return ApiResponse.success(201, "Staff created", staffService.create(request));
    }

    /** PUT /staff/{id} — cập nhật thợ; skills != null thay toàn bộ danh sách kỹ năng. */
    @PutMapping("/{id}")
    public ApiResponse<StaffDTO> update(@PathVariable Long id, @RequestBody @Valid StaffRequest request) {
        return ApiResponse.success(200, "Staff updated", staffService.update(id, request));
    }

    /** DELETE /staff/{id} — soft delete (giữ lịch sử booking/commission). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        staffService.deactivate(id);
        return ApiResponse.success(200, "Staff deactivated", null);
    }

    // ==================== Skills ====================

    /** GET /staff/{id}/skills — danh sách kỹ năng của thợ. */
    @GetMapping("/{id}/skills")
    public ApiResponse<List<StaffDTO.Skill>> skills(@PathVariable Long id) {
        return ApiResponse.success(200, "Success", staffService.get(id).getSkills());
    }

    /** PUT /staff/{id}/skills — thay toàn bộ danh sách kỹ năng (ADMIN/MANAGER). */
    @PutMapping("/{id}/skills")
    public ApiResponse<List<StaffDTO.Skill>> replaceSkills(@PathVariable Long id,
                                                           @RequestBody @Valid List<SkillRequest> skills) {
        return ApiResponse.success(200, "Skills updated", staffService.replaceSkills(id, skills));
    }

    // ==================== Shifts ====================

    /** GET /staff/{id}/shifts?from=&to= — ca làm trong khoảng ngày. */
    @GetMapping("/{id}/shifts")
    public ApiResponse<List<ShiftDTO>> shifts(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(200, "Success", staffService.listShifts(id, from, to, jwt));
    }

    /** POST /staff/{id}/shifts — thêm ca (chặn ca chồng lấn). */
    @PostMapping("/{id}/shifts")
    public ApiResponse<ShiftDTO> addShift(@PathVariable Long id, @RequestBody @Valid ShiftRequest request) {
        return ApiResponse.success(201, "Shift created", staffService.addShift(id, request));
    }

    /** DELETE /staff/{id}/shifts/{shiftId}. */
    @DeleteMapping("/{id}/shifts/{shiftId}")
    public ApiResponse<Void> deleteShift(@PathVariable Long id, @PathVariable Long shiftId) {
        staffService.deleteShift(id, shiftId);
        return ApiResponse.success(200, "Shift deleted", null);
    }

    // ==================== Time off ====================

    /** GET /staff/{id}/time-off. */
    @GetMapping("/{id}/time-off")
    public ApiResponse<List<TimeOffDTO>> timeOff(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(200, "Success", staffService.listTimeOff(id, jwt));
    }

    /** POST /staff/{id}/time-off — ADMIN/MANAGER đăng ký hộ hoặc chính thợ tự đăng ký. */
    @PostMapping("/{id}/time-off")
    public ApiResponse<TimeOffDTO> addTimeOff(@PathVariable Long id,
                                              @RequestBody @Valid TimeOffRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(201, "Time off created", staffService.addTimeOff(id, request, jwt));
    }

    /** DELETE /staff/{id}/time-off/{timeOffId} — huỷ nghỉ phép (soft: status CANCELLED). */
    @DeleteMapping("/{id}/time-off/{timeOffId}")
    public ApiResponse<Void> cancelTimeOff(@PathVariable Long id, @PathVariable Long timeOffId) {
        staffService.cancelTimeOff(id, timeOffId);
        return ApiResponse.success(200, "Time off cancelled", null);
    }

    // ==================== Bookings of a staff ====================

    /** GET /staff/{id}/bookings — ADMIN/MANAGER/RECEPTIONIST hoặc chính thợ. */
    @GetMapping("/{id}/bookings")
    public ApiResponse<List<BookingResponse>> bookings(@PathVariable Long id,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(200, "Success", staffService.bookingsOfStaff(id, jwt));
    }
}
