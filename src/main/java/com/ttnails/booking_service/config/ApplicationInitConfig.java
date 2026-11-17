package com.ttnails.booking_service.config;

import com.ttnails.booking_service.common.enums.Role;
import com.ttnails.booking_service.common.enums.SkillLevel;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.staff.entity.Branch;
import com.ttnails.booking_service.staff.entity.Shift;
import com.ttnails.booking_service.staff.entity.Staff;
import com.ttnails.booking_service.staff.entity.StaffSkill;
import com.ttnails.booking_service.staff.repository.BranchRepository;
import com.ttnails.booking_service.staff.repository.ShiftRepository;
import com.ttnails.booking_service.staff.repository.StaffRepository;
import com.ttnails.booking_service.staff.repository.StaffSkillRepository;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Seed dữ liệu khởi động — idempotent, chạy cho cả MySQL (Flyway) và H2 (create-drop khi test).
 *
 * <p>Thứ tự: admin → dịch vụ → chi nhánh → thợ + kỹ năng → ca làm 14 ngày.
 * Ca làm chỉ sinh cho Mon–Sat (D5: Chủ nhật nghỉ).</p>
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfig {

    static final LocalTime OPEN_TIME = LocalTime.of(9, 0);
    static final LocalTime CLOSE_TIME = LocalTime.of(18, 0);

    PasswordEncoder passwordEncoder;

    /** Mật khẩu admin đọc từ biến môi trường — KHÔNG hardcode password mặc định nữa */
    @Value("${ADMIN_PASSWORD:}")
    @NonFinal
    String adminPassword;

    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository,
                                        ServiceRepository serviceRepository,
                                        BranchRepository branchRepository,
                                        StaffRepository staffRepository,
                                        StaffSkillRepository staffSkillRepository,
                                        ShiftRepository shiftRepository) {
        return args -> {
            seedAdmin(userRepository);
            seedServices(serviceRepository);
            Branch branch = seedBranch(branchRepository);
            seedStaff(staffRepository, staffSkillRepository, serviceRepository, branch);
            seedShifts(shiftRepository, staffRepository);
        };
    }

    private void seedAdmin(UserRepository userRepository) {
        if (!userRepository.findByRolesContaining(Role.ADMIN.name()).isEmpty()) {
            return;
        }
        var roles = new HashSet<String>();
        roles.add(Role.ADMIN.name());

        String rawPassword;
        if (adminPassword != null && !adminPassword.isBlank()) {
            rawPassword = adminPassword;
            log.info("Created default admin (admin@gmail.com) with password from ADMIN_PASSWORD env");
        } else {
            // Không đặt ADMIN_PASSWORD → sinh mật khẩu ngẫu nhiên, in 1 lần duy nhất trong log khởi động
            rawPassword = UUID.randomUUID().toString();
            log.warn("ADMIN_PASSWORD is not set! Generated random password for admin@gmail.com (change it after first login): {}", rawPassword);
        }

        userRepository.save(User.builder()
                .name("admin")
                .password(passwordEncoder.encode(rawPassword))
                .email("admin@gmail.com")
                .roles(roles)
                .build());
    }

    private void seedServices(ServiceRepository serviceRepository) {
        // Seed dịch vụ mẫu để trang Booking + Admin có dữ liệu thật ngay
        if (serviceRepository.count() > 0) {
            return;
        }
        serviceRepository.saveAll(List.of(
                Service.builder().name("Maniküre")
                        .description("Klassische Nagelpflege mit Lack in Wunschfarbe")
                        .duration(45).price(28.0).kategorie("Nagelpflege").beliebtheit("hoch").build(),
                Service.builder().name("Pediküre")
                        .description("Inklusive Fußbad, Nagelpflege und Fußmassage")
                        .duration(60).price(35.0).kategorie("Nagelpflege").beliebtheit("hoch").build(),
                Service.builder().name("Shellac / Gellack")
                        .description("Langlebiger Gel-Lack mit UV-Härtung")
                        .duration(60).price(35.0).kategorie("Nageldesign").beliebtheit("hoch").build(),
                Service.builder().name("Nagelverlängerung")
                        .description("Verlängerung mit Tips oder Gel")
                        .duration(90).price(55.0).kategorie("Nageldesign").beliebtheit("mittel").build(),
                Service.builder().name("Nageldesign Fullset")
                        .description("Individuelles Design nach Kundenwunsch")
                        .duration(75).price(45.0).kategorie("Nageldesign").beliebtheit("mittel").build(),
                Service.builder().name("Nail-Art")
                        .description("Kreative Motive, Strass und Stempel-Design")
                        .duration(30).price(15.0).kategorie("Nageldesign").beliebtheit("niedrig").build()
        ));
        log.warn("Seeded default salon services (6 items)");
    }

    private Branch seedBranch(BranchRepository branchRepository) {
        return branchRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseGet(() -> {
                    Branch saved = branchRepository.save(Branch.builder()
                            .name("TT Nails")
                            .timezone("Europe/Berlin")
                            .openTime(OPEN_TIME)
                            .closeTime(CLOSE_TIME)
                            .slotStepMinutes(30)
                            .bufferMinutes(10)
                            .phone("+49 30 1234567")
                            .address("Musterstraße 1, 10115 Berlin")
                            .active(true)
                            .build());
                    log.warn("Seeded default branch (id={})", saved.getId());
                    return saved;
                });
    }

    private void seedStaff(StaffRepository staffRepository,
                           StaffSkillRepository staffSkillRepository,
                           ServiceRepository serviceRepository,
                           Branch branch) {
        if (staffRepository.count() > 0) {
            return;
        }
        List<Service> services = serviceRepository.findAll();
        List<Staff> staff = List.of(
                Staff.builder().branch(branch).displayName("Minh").color("#E91E63")
                        .active(true).commissionRate(new BigDecimal("0.4000")).build(),
                Staff.builder().branch(branch).displayName("Linh").color("#3F51B5")
                        .active(true).commissionRate(new BigDecimal("0.4000")).build(),
                Staff.builder().branch(branch).displayName("Hoa").color("#009688")
                        .active(true).commissionRate(new BigDecimal("0.4000")).build()
        );
        staffRepository.saveAll(staff);

        // MVP: mọi thợ làm được mọi dịch vụ. Admin có thể thu hẹp qua PUT /staff/{id}/skills.
        List<StaffSkill> skills = staff.stream()
                .flatMap(s -> services.stream().map(sv -> StaffSkill.builder()
                        .staff(s).service(sv).level(SkillLevel.ADVANCED).build()))
                .toList();
        staffSkillRepository.saveAll(skills);
        log.warn("Seeded {} staff with {} skills", staff.size(), skills.size());
    }

    private void seedShifts(ShiftRepository shiftRepository, StaffRepository staffRepository) {
        if (shiftRepository.count() > 0) {
            return;
        }
        List<Staff> staff = staffRepository.findAll();
        if (staff.isEmpty()) {
            return;
        }
        LocalDate from = LocalDate.now();
        List<Shift> shifts = new java.util.ArrayList<>();
        for (int day = 0; day < 14; day++) {
            LocalDate date = from.plusDays(day);
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            for (Staff member : staff) {
                shifts.add(Shift.builder()
                        .staff(member)
                        .workDate(date)
                        .startTime(OPEN_TIME)
                        .endTime(CLOSE_TIME)
                        .build());
            }
        }
        shiftRepository.saveAll(shifts);
        log.warn("Seeded {} shifts (Mon-Sat, 14 days)", shifts.size());
    }
}
