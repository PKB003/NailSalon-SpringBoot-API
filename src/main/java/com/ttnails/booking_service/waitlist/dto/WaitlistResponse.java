package com.ttnails.booking_service.waitlist.dto;

import com.ttnails.booking_service.waitlist.entity.WaitlistEntry;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Response của /waitlist. */
@Data
public class WaitlistResponse {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private LocalDate desiredDate;
    private LocalTime desiredStartTime;
    private String name;
    private String phone;
    private String email;
    private String status;
    private LocalDateTime notifiedAt;
    private LocalDateTime createdAt;

    public static WaitlistResponse from(WaitlistEntry entry) {
        WaitlistResponse dto = new WaitlistResponse();
        dto.id = entry.getId();
        dto.serviceId = entry.getServiceId();
        dto.desiredDate = entry.getDesiredDate();
        dto.desiredStartTime = entry.getDesiredStartTime();
        dto.name = entry.getName();
        dto.phone = entry.getPhone();
        dto.email = entry.getEmail();
        dto.status = entry.getStatus();
        dto.notifiedAt = entry.getNotifiedAt();
        dto.createdAt = entry.getCreatedAt();
        return dto;
    }
}
