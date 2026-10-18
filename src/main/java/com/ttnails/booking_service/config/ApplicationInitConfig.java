package com.ttnails.booking_service.config;

import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.common.enums.Role;
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

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfig {
    PasswordEncoder passwordEncoder;

    /** Mật khẩu admin đọc từ biến môi trường — KHÔNG hardcode password mặc định nữa */
    @Value("${ADMIN_PASSWORD:}")
    @NonFinal
    String adminPassword;

    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository, ServiceRepository serviceRepository) {
        return args -> {
            if(userRepository.findByRolesContaining(Role.ADMIN.name()).isEmpty()) {
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

                User user = User.builder()
                        .name("admin")
                        .password(passwordEncoder.encode(rawPassword))
                        .email("admin@gmail.com")
                        .roles(roles)
                        .build();
                userRepository.save(user);
            }

            // Seed dịch vụ mẫu để trang Booking + Admin có dữ liệu thật ngay
            if (serviceRepository.count() == 0) {
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
        };
    }
}
