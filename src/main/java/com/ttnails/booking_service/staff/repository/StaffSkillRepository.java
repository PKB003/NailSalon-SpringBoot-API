package com.ttnails.booking_service.staff.repository;

import com.ttnails.booking_service.staff.entity.StaffSkill;
import com.ttnails.booking_service.staff.entity.StaffSkillId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StaffSkillRepository extends JpaRepository<StaffSkill, StaffSkillId> {

    @EntityGraph(attributePaths = {"service"})
    List<StaffSkill> findByStaffId(Long staffId);

    @EntityGraph(attributePaths = {"service"})
    List<StaffSkill> findByStaffIdIn(Collection<Long> staffIds);

    void deleteByStaffId(Long staffId);
}
