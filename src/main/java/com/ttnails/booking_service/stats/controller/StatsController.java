package com.ttnails.booking_service.stats.controller;

import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.stats.dto.DashboardStats;
import com.ttnails.booking_service.stats.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/stats")
public class StatsController {
    @Autowired
    private StatsService statsService;

    /** GET /stats/dashboard — số liệu cho Admin Dashboard (chỉ ADMIN) */
    @GetMapping("/dashboard")
    public ApiResponse<DashboardStats> getDashboardStats() {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), statsService.getDashboardStats());
    }
}
