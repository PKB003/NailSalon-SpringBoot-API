package com.ttnails.booking_service.staff.dto;

import com.ttnails.booking_service.common.enums.SkillLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Thông tin thợ trả về FE. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffDTO {
    private Long id;
    private String displayName;
    private String color;
    private boolean active;
    private BigDecimal commissionRate;
    private Long branchId;
    private java.util.UUID userId;
    private java.util.List<Skill> skills;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Skill {
        private Long serviceId;
        private String serviceName;
        private Integer duration;
        private Double price;
        private SkillLevel level;
    }
}
