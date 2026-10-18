package com.ttnails.booking_service.stats.service;

import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.stats.dto.DashboardStats;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StatsService {
    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @PreAuthorize("hasRole('ADMIN')")
    public DashboardStats getDashboardStats() {
        List<Booking> allBookings = bookingRepository.findAll();

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);

        // Giá dịch vụ: tra theo serviceId, fallback theo tên dịch vụ
        Map<Long, Double> priceById = new HashMap<>();
        Map<String, Double> priceByName = new HashMap<>();
        serviceRepository.findAll().forEach(s -> {
            if (s.getPrice() != null) {
                priceById.put(s.getId(), s.getPrice());
                priceByName.put(s.getName(), s.getPrice());
            }
        });

        double monthlyRevenue = 0;
        double lastMonthRevenue = 0;
        long todayBookings = 0;
        long yesterdayBookings = 0;
        Map<String, Long> serviceCount = new HashMap<>();

        for (Booking booking : allBookings) {
            if (BookingStatus.CANCELLED.equals(booking.getStatus())) continue;
            LocalDateTime dt = booking.getPreferredDateTime();
            if (dt == null) continue;

            LocalDate date = dt.toLocalDate();
            double price = resolvePrice(priceById, priceByName, booking);

            if (date.equals(today)) todayBookings++;
            if (date.equals(yesterday)) yesterdayBookings++;

            if (!date.isBefore(monthStart)) {
                monthlyRevenue += price;
                serviceCount.merge(booking.getService(), 1L, Long::sum);
            } else if (!date.isBefore(lastMonthStart)) {
                lastMonthRevenue += price;
            }
        }

        String topService = serviceCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        long bookingsDiff = todayBookings - yesterdayBookings;
        String bookingsTrend = (bookingsDiff >= 0 ? "+" : "") + bookingsDiff;

        return DashboardStats.builder()
                .todayBookings(todayBookings)
                .monthlyRevenue(monthlyRevenue)
                .totalCustomers(userRepository.count())
                .topService(topService)
                .bookingsTrend(bookingsTrend)
                .revenueTrend(formatTrend(monthlyRevenue, lastMonthRevenue))
                .build();
    }

    private double resolvePrice(Map<Long, Double> priceById, Map<String, Double> priceByName, Booking booking) {
        if (booking.getServiceId() != null && priceById.containsKey(booking.getServiceId())) {
            return priceById.get(booking.getServiceId());
        }
        if (booking.getService() != null && priceByName.containsKey(booking.getService())) {
            return priceByName.get(booking.getService());
        }
        return 0;
    }

    /** Ví dụ: "+12,5%" (định dạng số thập phân kiểu Đức) */
    private String formatTrend(double current, double previous) {
        if (previous <= 0) {
            return current > 0 ? "+100%" : "+0%";
        }
        double pct = (current - previous) / previous * 100;
        return (pct >= 0 ? "+" : "") + String.format(Locale.GERMAN, "%.1f", pct) + "%";
    }
}
