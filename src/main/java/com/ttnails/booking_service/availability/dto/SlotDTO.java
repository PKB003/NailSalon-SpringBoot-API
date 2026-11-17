package com.ttnails.booking_service.availability.dto;

/** Một slot trả về FE: {@code {"start":"09:00","end":"10:45"}} (giờ xong dịch vụ, không gồm buffer). */
public record SlotDTO(String start, String end) {
}
