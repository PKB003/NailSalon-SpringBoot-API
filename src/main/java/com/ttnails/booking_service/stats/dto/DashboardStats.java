package com.ttnails.booking_service.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stats shape cho Admin Dashboard:
 * { todayBookings, monthlyRevenue, totalCustomers, topService, bookingsTrend, revenueTrend }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    private long todayBookings;
    private double monthlyRevenue;
    private long totalCustomers;
    private String topService;
    private String bookingsTrend;
    private String revenueTrend;
}
