package com.ttnails.booking_service.bookings.dto;

import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.bookings.entity.Booking;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Response shape cho frontend (contract /bookings).
 * Trả song song field tiếng Anh (client view) và tiếng Đức (admin view)
 * để cả 2 view dùng chung 1 shape.
 */
@Data
public class BookingResponse {
    private Long id;
    private Long serviceId;

    private String service;
    private String dienstleistung;

    private String date;
    private String datum;

    private String time;
    private String uhrzeit;

    private String name;
    private String kunde;

    private String phone;
    private String email;
    private String note;

    private String employee;
    private String mitarbeiter;

    private String status;
    private String price;

    public BookingResponse(Booking booking, String price) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        LocalDateTime dt = booking.getPreferredDateTime();
        String serviceName = booking.getService();

        String fullName = null;
        if (booking.getFirstName() != null && booking.getLastName() != null) {
            fullName = booking.getFirstName() + " " + booking.getLastName();
        } else if (booking.getFirstName() != null) {
            fullName = booking.getFirstName();
        } else {
            fullName = booking.getLastName();
        }

        this.id = booking.getId();
        this.serviceId = booking.getServiceId();
        this.service = serviceName;
        this.dienstleistung = serviceName;
        this.date = dt != null ? dt.format(dateFmt) : null;
        this.datum = this.date;
        this.time = dt != null ? dt.format(timeFmt) : null;
        this.uhrzeit = this.time;
        this.name = fullName;
        this.kunde = fullName;
        this.phone = booking.getPhone();
        this.email = booking.getEmail();
        this.note = booking.getNote();
        this.employee = booking.getEmployee();
        this.mitarbeiter = this.employee;
        this.status = booking.getStatus() != null ? booking.getStatus() : BookingStatus.IN_PROGRESS;
        this.price = price;
    }
}
