package com.ttnails.booking_service.config;

import com.ttnails.booking_service.entity.User;
import com.ttnails.booking_service.enums.Role;
import com.ttnails.booking_service.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfig {
    PasswordEncoder passwordEncoder;
    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository) {
        return args -> {
            if(userRepository.findByRolesContaining(Role.ADMIN.name()).isEmpty()) {
                var roles = new HashSet<String>();
                roles.add(Role.ADMIN.name());

                User user = User.builder()
                        .name("admin")
                        .password(passwordEncoder.encode("12345678"))
                        .email("admin@gmail.com")
                        .roles(roles)
                        .build();
                userRepository.save(user);
                log.warn("Create a default admin: name(admin), email(admin@gmail.com), password(12345678)");
            }
        };
    }
}
