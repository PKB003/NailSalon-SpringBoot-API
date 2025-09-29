package com.ttnails.booking_service.dto;

import com.ttnails.booking_service.entity.Booking;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class BookingDTO {
    private Long id;
    private String service;
    private LocalDateTime preferredDateAndTime;
    private String note;
    private String userName;
    private String userEmail;
    private String userPhone;

    public BookingDTO(Booking booking) {
        this.id = booking.getId();
        this.service = booking.getService();
        this.preferredDateAndTime = booking.getPreferredDateTime();
        this.note = booking.getNote();
        if (booking.getUser() != null) {
            this.userName = booking.getUser().getName();
            this.userEmail = booking.getUser().getEmail();
            this.userPhone = booking.getUser().getPhone();
        }
    }
}
