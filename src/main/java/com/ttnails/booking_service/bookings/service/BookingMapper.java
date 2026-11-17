package com.ttnails.booking_service.bookings.service;

import com.ttnails.booking_service.bookings.dto.BookingResponse;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.entity.BookingItem;
import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Chuyển {@link Booking} → {@link BookingResponse}.
 *
 * <p>Giá trả về lấy từ snapshot tại thời điểm đặt ({@code priceSnapshot}); chỉ booking
 * legacy chưa có snapshot mới fallback về giá live của service (B-06).</p>
 */
@Component
public class BookingMapper {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ServiceRepository serviceRepository;

    public BookingMapper(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public BookingResponse toResponse(Booking booking) {
        return toResponse(booking, null);
    }

    public BookingResponse toResponse(Booking booking, List<BookingItem> items) {
        BookingResponse response = new BookingResponse();
        LocalDateTime start = booking.getStartAt() != null ? booking.getStartAt() : booking.getPreferredDateTime();
        LocalDateTime end = booking.getEndAt() != null ? booking.getEndAt() : booking.resolveEndAt();

        String fullName = fullName(booking);
        response.setId(booking.getId());
        response.setServiceId(booking.getServiceId());
        response.setService(booking.getService());
        response.setDienstleistung(booking.getService());
        response.setDate(start != null ? start.format(DATE) : null);
        response.setDatum(response.getDate());
        response.setTime(start != null ? start.format(TIME) : null);
        response.setUhrzeit(response.getTime());
        response.setEndDate(end != null ? end.format(DATE) : null);
        response.setEndTime(end != null ? end.format(TIME) : null);
        response.setEndUhrzeit(response.getEndTime());
        response.setName(fullName);
        response.setKunde(fullName);
        response.setPhone(booking.getPhone());
        response.setEmail(booking.getEmail());
        response.setNote(booking.getNote());

        String staffName = booking.getStaff() != null ? booking.getStaff().getDisplayName() : booking.getEmployee();
        response.setEmployee(staffName);
        response.setMitarbeiter(staffName);
        response.setStaffName(staffName);
        response.setStaffId(booking.getStaff() != null ? booking.getStaff().getId() : null);

        response.setStatus(booking.getStatus() != null ? booking.getStatus() : BookingStatus.IN_PROGRESS);
        response.setCurrency(booking.getCurrency() != null ? booking.getCurrency() : "EUR");

        BigDecimal price = booking.getPriceSnapshot();
        if (price == null) {
            price = resolveLivePrice(booking);
        }
        response.setPriceSnapshot(price);
        response.setPrice(formatPrice(price));
        response.setTotalDurationMinutes(start != null && end != null
                ? (int) ChronoUnit.MINUTES.between(start, end) : null);

        if (items != null) {
            response.setItems(items.stream().map(this::toItem).toList());
        }
        return response;
    }

    private BookingResponse.Item toItem(BookingItem item) {
        BookingResponse.Item dto = new BookingResponse.Item();
        dto.setServiceId(item.getService().getId());
        dto.setName(item.getService().getName());
        dto.setStartTime(item.getStartAt() != null ? item.getStartAt().format(TIME) : null);
        dto.setEndTime(item.getEndAt() != null ? item.getEndAt().format(TIME) : null);
        dto.setDuration(item.getDuration());
        dto.setPrice(item.getPrice());
        dto.setCurrency(item.getCurrency());
        dto.setStaffId(item.getStaff() != null ? item.getStaff().getId() : null);
        dto.setStaffName(item.getStaff() != null ? item.getStaff().getDisplayName() : null);
        return dto;
    }

    private String fullName(Booking booking) {
        if (booking.getFirstName() != null && booking.getLastName() != null) {
            return booking.getFirstName() + " " + booking.getLastName();
        }
        return booking.getFirstName() != null ? booking.getFirstName() : booking.getLastName();
    }

    private BigDecimal resolveLivePrice(Booking booking) {
        if (booking.getServiceId() != null) {
            return serviceRepository.findById(booking.getServiceId())
                    .map(s -> s.getPrice() != null ? BigDecimal.valueOf(s.getPrice()) : BigDecimal.ZERO)
                    .orElse(BigDecimal.ZERO);
        }
        if (booking.getService() != null) {
            return serviceRepository.findByName(booking.getService())
                    .map(s -> s.getPrice() != null ? BigDecimal.valueOf(s.getPrice()) : BigDecimal.ZERO)
                    .orElse(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }

    /** "35 €" hoặc "32,50 €" — giữ đúng format frontend đang dùng. */
    public static String formatPrice(BigDecimal price) {
        BigDecimal safe = price == null ? BigDecimal.ZERO : price.setScale(2, RoundingMode.HALF_UP);
        if (safe.stripTrailingZeros().scale() <= 0) {
            return safe.toBigInteger() + " €";
        }
        return String.format(Locale.GERMAN, "%.2f €", safe);
    }
}
