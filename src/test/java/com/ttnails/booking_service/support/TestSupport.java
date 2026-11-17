package com.ttnails.booking_service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.common.enums.Role;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tiện ích dùng chung cho test API (S1): đăng nhập lấy JWT, chọn ngày làm việc,
 * tạo user với role tuỳ ý.
 */
public final class TestSupport {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final String ADMIN_EMAIL = "admin@gmail.com";
    public static final String ADMIN_PASSWORD = "12345678";
    public static final String PASSWORD = "Test12345";

    private TestSupport() {
    }

    /** Đăng nhập và trả access token. */
    public static String login(MockMvc mockMvc, ObjectMapper mapper, String emailOrPhone, String password)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrPhone\":\"" + emailOrPhone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asText();
    }

    public static String adminToken(MockMvc mockMvc, ObjectMapper mapper) throws Exception {
        return login(mockMvc, mapper, ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    /**
     * Ngày làm việc kế tiếp (bỏ Chủ nhật) tính từ hôm nay — dùng cho booking/availability.
     * Luôn nằm trong 14 ngày seed ca làm.
     */
    public static LocalDate workdayAfter(int minOffsetDays) {
        LocalDate date = LocalDate.now().plusDays(Math.max(minOffsetDays, 1));
        while (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    /** Chủ nhật kế tiếp trong 14 ngày tới (để test ngày nghỉ), hoặc null nếu không có. */
    public static LocalDate nextSunday() {
        LocalDate date = LocalDate.now().plusDays(1);
        for (int i = 0; i < 13; i++) {
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) return date;
            date = date.plusDays(1);
        }
        return null;
    }

    /** Tạo user (nếu chưa có) với role chỉ định và trả token. */
    public static String userTokenWithRole(MockMvc mockMvc, ObjectMapper mapper, UserRepository userRepository,
                                           PasswordEncoder encoder, String email, Role role) throws Exception {
        if (userRepository.findByEmail(email).isEmpty()) {
            Set<String> roles = new HashSet<>();
            roles.add(role.name());
            userRepository.save(User.builder()
                    .name("Test " + role.name())
                    .email(email)
                    .phone("0" + Math.abs(UUID.randomUUID().hashCode()) % 1000000000L)
                    .password(encoder.encode(PASSWORD))
                    .roles(roles)
                    .build());
        }
        return login(mockMvc, mapper, email, PASSWORD);
    }

    /**
     * Thêm ca 09:00–18:00 cho một thợ trong ngày — cần thiết vì thợ tạo qua API
     * không có ca mặc định (khác với 3 thợ seed sẵn).
     *
     * <p>Idempotent: nếu ca đã tồn tại (409 {@code SHIFT_OVERLAP}) thì bỏ qua, để
     * {@code @BeforeEach} có thể chạy lại trên cùng DB H2 giữa các test method.</p>
     */
    public static void addFullDayShift(MockMvc mockMvc, String adminToken, long staffId, LocalDate date)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/staff/" + staffId + "/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"" + date + "\",\"startTime\":\"09:00\",\"endTime\":\"18:00\"}"))
                .andReturn();
        int status = result.getResponse().getStatus();
        if (status != 200 && status != 201 && status != 409) {
            throw new AssertionError("Unexpected status when seeding shift: " + status
                    + " body=" + result.getResponse().getContentAsString());
        }
    }

    /** Thêm ca 09:00–18:00 cho 14 ngày tới (bỏ Chủ nhật) — dùng cho test cần nhiều ngày. */
    public static void addFullDayShiftsForTwoWeeks(MockMvc mockMvc, String adminToken, long staffId)
            throws Exception {
        LocalDate from = LocalDate.now();
        for (int day = 0; day < 14; day++) {
            LocalDate date = from.plusDays(day);
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            addFullDayShift(mockMvc, adminToken, staffId, date);
        }
    }
}
