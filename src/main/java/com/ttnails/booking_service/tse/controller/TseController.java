package com.ttnails.booking_service.tse.controller;

import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.tse.dto.TseStatusResponse;
import com.ttnails.booking_service.tse.service.TseStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API vận hành TSE (S3, Plan §8 API table).
 *
 * <p>Chỉ MANAGER/ADMIN xem được. Không expose secret; TSS/client id đã mask.</p>
 */
@RestController
@RequestMapping("/tse")
@RequiredArgsConstructor
public class TseController {

    private final TseStatusService tseStatusService;

    /** GET /tse/status — TSE có bật không, provider nào, đã có credential chưa. */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<TseStatusResponse> status() {
        return ApiResponse.success(200, "Success", tseStatusService.getStatus());
    }
}
