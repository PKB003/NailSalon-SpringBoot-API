package com.ttnails.booking_service.staff.dto;

import com.ttnails.booking_service.common.enums.SkillLevel;
import jakarta.validation.constraints.NotNull;

/** Gán một dịch vụ cho thợ kèm mức độ. */
public record SkillRequest(@NotNull(message = "serviceId is required") Long serviceId,
                           SkillLevel level) {

    public SkillLevel levelOrDefault() {
        return level != null ? level : SkillLevel.BASIC;
    }
}
