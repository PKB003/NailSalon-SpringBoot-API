package com.ttnails.booking_service.availability;

import com.ttnails.booking_service.availability.dto.AvailabilityResponse;
import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * S1-04 — {@code GET /availability?date=YYYY-MM-DD&serviceIds=1,2&staffId=3}.
 *
 * <p>Public (khách chưa đăng nhập vẫn xem được slot). Endpoint cũ
 * {@code GET /bookings/availability} giữ làm deprecated, trả cùng dữ liệu
 * theo dịch vụ ngắn nhất.</p>
 */
@RestController
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    /**
     * @param serviceIds 1..3 id dịch vụ (nhận cả {@code serviceIds=1,2} và {@code serviceIds=1&serviceIds=2})
     * @param serviceId  alias đơn lẻ cho client cũ
     * @param staffId    optional — null = mọi thợ rảnh
     */
    @GetMapping("/availability")
    public ApiResponse<AvailabilityResponse> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String date,
            @RequestParam(required = false) List<Long> serviceIds,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long staffId) {

        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new AppException(ErrorCode.INVALID_DATE);
        }

        List<Long> ids = new ArrayList<>();
        if (serviceIds != null) {
            ids.addAll(serviceIds.stream().filter(java.util.Objects::nonNull).toList());
        }
        if (ids.isEmpty() && serviceId != null) {
            ids.add(serviceId);
        }
        if (ids.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        AvailabilityService.DayAvailability day =
                availabilityService.computeDay(parsed, ids, staffId);
        return ApiResponse.success(200, "Success", availabilityService.toResponse(day));
    }
}
