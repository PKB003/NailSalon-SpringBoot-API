package com.ttnails.booking_service.staff.entity;

import com.ttnails.booking_service.common.enums.SkillLevel;
import com.ttnails.booking_service.services.entity.Service;
import jakarta.persistence.*;
import lombok.*;

/** Gán kỹ năng: thợ nào làm được dịch vụ nào (S1-01). */
@Entity
@Table(name = "staff_skill")
@IdClass(StaffSkillId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffSkill {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillLevel level;
}
