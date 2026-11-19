package com.ttnails.booking_service.waitlist.controller;

import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.common.dto.PageResponse;
import com.ttnails.booking_service.waitlist.dto.WaitlistRequest;
import com.ttnails.booking_service.waitlist.dto.WaitlistResponse;
import com.ttnails.booking_service.waitlist.service.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Waitlist (S2-04).
 *
 * <ul>
 *   <li>{@code POST /waitlist} — public (guest hoặc user), chống spam bằng unique key.</li>
 *   <li>{@code GET /waitlist} — ADMIN/MANAGER/RECEPTIONIST, lọc theo ngày/dịch vụ/trạng thái.</li>
 *   <li>{@code DELETE /waitlist/{id}?phone=} — khách tự huỷ (khớp SĐT) hoặc staff huỷ hộ.</li>
 * </ul>
 */
@RestController
@RequestMapping("/waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping
    public ApiResponse<WaitlistResponse> create(@RequestBody @Valid WaitlistRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(201, "Waitlist entry created", waitlistService.create(request, jwt));
    }

    @GetMapping
    public ApiResponse<PageResponse<WaitlistResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(200, "Success", waitlistService.list(date, serviceId, status, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable Long id,
                                    @RequestParam(required = false) String phone,
                                    @AuthenticationPrincipal Jwt jwt) {
        waitlistService.cancel(id, phone, jwt);
        return ApiResponse.success(200, "Waitlist entry cancelled", null);
    }
}
