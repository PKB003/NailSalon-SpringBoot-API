package com.ttnails.booking_service.staff.entity;

import java.io.Serializable;
import java.util.Objects;

/** Khóa ghép của {@link StaffSkill} — khớp PRIMARY KEY (staff_id, service_id). */
public class StaffSkillId implements Serializable {

    private Long staff;
    private Long service;

    public StaffSkillId() {
    }

    public StaffSkillId(Long staff, Long service) {
        this.staff = staff;
        this.service = service;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StaffSkillId that)) return false;
        return Objects.equals(staff, that.staff) && Objects.equals(service, that.service);
    }

    @Override
    public int hashCode() {
        return Objects.hash(staff, service);
    }
}
