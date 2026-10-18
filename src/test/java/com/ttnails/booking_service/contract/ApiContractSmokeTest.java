package com.ttnails.booking_service.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test xác thực toàn bộ API contract giữa frontend và backend
 * (login → services → availability → booking → my → cancel/status → customers → stats).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiContractSmokeTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    UserRepository userRepository;

    private String loginAndGetToken(String emailOrPhone, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrPhone\":\"" + emailOrPhone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asText();
    }

    private JsonNode getBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * Slot hợp lệ kế tiếp trong giờ mở cửa (09:00–18:00, bước 30') — luôn ở tương lai
     * và cách hiện tại ít hơn 24h (dùng để test chính sách hủy CANCEL_TOO_LATE).
     */
    private LocalDateTime nextValidOpenSlot() {
        LocalDateTime candidate = LocalDateTime.now().plusHours(3).withSecond(0).withNano(0);
        boolean firstHalf = candidate.getMinute() <= 30;
        candidate = candidate.withMinute(firstHalf ? 30 : 0);
        if (!firstHalf) candidate = candidate.plusHours(1);

        LocalTime open = LocalTime.of(9, 0);
        LocalTime close = LocalTime.of(18, 0);
        if (candidate.toLocalTime().isBefore(open)) {
            candidate = candidate.withHour(9).withMinute(0);
        } else if (!candidate.toLocalTime().isBefore(close)) {
            candidate = candidate.plusDays(1).withHour(9).withMinute(0);
        }
        return candidate;
    }

    @Test
    void fullApiContractFlow() throws Exception {
        // 0. GET /services — public, có seed data
        MvcResult servicesResult = mockMvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        long serviceId = getBody(servicesResult).get("data").get(0).get("id").asLong();

        // 1. POST /auth/register → { token, user{ role: "client" } }
        String registerBody = "{\"name\":\"Anna Müller\",\"email\":\"anna@test.de\"," +
                "\"phone\":\"0176 12345678\",\"password\":\"geheim123\",\"birthday\":\"1990-03-15\"}";
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("client"))
                .andExpect(jsonPath("$.data.user.birthday").value("1990-03-15"))
                .andReturn();
        String userToken = getBody(registerResult).get("data").get("token").asText();

        // 2. GET /auth/me
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("anna@test.de"));

        // 3. PUT /auth/me
        mockMvc.perform(put("/auth/me").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anna M.\",\"birthday\":\"1991-01-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Anna M."));

        // 4. PUT /auth/password → { success: true }
        mockMvc.perform(put("/auth/password").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"geheim123\",\"newPassword\":\"geheim456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        // login lại bằng emailOrPhone với password mới
        String userToken2 = loginAndGetToken("anna@test.de", "geheim456");

        // 5. GET /bookings/availability → { slots: ["09:00", ...] }
        String tomorrow = LocalDate.now().plusDays(1).toString();
        mockMvc.perform(get("/bookings/availability").param("date", tomorrow))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots").isArray())
                .andExpect(jsonPath("$.data.slots[0]").value("09:00"));

        // 6. POST /bookings → booking với id làm mã xác nhận, status "in Bearbeitung"
        String bookingBody = "{\"serviceId\":" + serviceId + ",\"date\":\"" + tomorrow + "\"," +
                "\"time\":\"14:30\",\"name\":\"Anna Müller\",\"phone\":\"0176 12345678\"," +
                "\"email\":\"anna@test.de\",\"note\":\"Design wünsche\"}";
        MvcResult bookingResult = mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + userToken2)
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("in Bearbeitung"))
                .andExpect(jsonPath("$.data.time").value("14:30"))
                .andExpect(jsonPath("$.data.uhrzeit").value("14:30"))
                .andReturn();
        long bookingId = getBody(bookingResult).get("data").get("id").asLong();

        // 7. GET /bookings/my
        mockMvc.perform(get("/bookings/my").header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value((int) bookingId))
                .andExpect(jsonPath("$.data[0].service").exists())
                .andExpect(jsonPath("$.data[0].price").exists());

        // 8. Admin login + các endpoint admin
        String adminToken = loginAndGetToken("admin@gmail.com", "12345678");

        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/customers").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].besuche").exists())
                .andExpect(jsonPath("$.data[?(@.email == 'anna@test.de')].telefon").exists());

        mockMvc.perform(get("/stats/dashboard").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayBookings").exists())
                .andExpect(jsonPath("$.data.monthlyRevenue").exists())
                .andExpect(jsonPath("$.data.totalCustomers").exists())
                .andExpect(jsonPath("$.data.bookingsTrend").exists())
                .andExpect(jsonPath("$.data.revenueTrend").exists());

        // 9. PATCH /bookings/:id/status (admin)
        mockMvc.perform(patch("/bookings/" + bookingId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"bestätigt\",\"employee\":\"Minh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("bestätigt"))
                .andExpect(jsonPath("$.data.employee").value("Minh"))
                .andExpect(jsonPath("$.data.mitarbeiter").value("Minh"))
                .andExpect(jsonPath("$.data.price").isNotEmpty());

        // 9b. COMPLETION_TOO_EARLY: lịch ngày mai chưa tới giờ mà đóng hoàn tất → 409
        mockMvc.perform(patch("/bookings/" + bookingId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"abgeschlossen\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Termin kann erst nach Beginn als abgeschlossen markiert werden"));

        // 9c. Guards terminal + lịch quá khứ: chèn trực tiếp booking hôm qua qua repository
        // (POST /bookings từ chối ngày quá khứ nên phải seed thẳng DB)
        User anna = userRepository.findByEmail("anna@test.de").orElseThrow();
        Booking past = new Booking();
        past.setFirstName("Anna Müller");
        past.setEmail("anna@test.de");
        past.setPhone("0176 12345678");
        past.setService("Maniküre");
        past.setPreferredDateTime(LocalDateTime.now().minusDays(1)
                .withHour(10).withMinute(0).withSecond(0).withNano(0));
        past.setStatus("in Bearbeitung");
        past.setUser(anna);
        past = bookingRepository.save(past);

        // Khách cancel lịch đã qua giờ → 400 APPOINTMENT_PASSED (không phải lỗi 24h)
        mockMvc.perform(patch("/bookings/" + past.getId() + "/cancel")
                        .header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Termin hat bereits stattgefunden und kann nicht mehr storniert werden"));

        // Admin đóng hoàn tất lịch quá khứ → 200 (đóng sổ sau phục vụ là flow chuẩn)
        mockMvc.perform(patch("/bookings/" + past.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"abgeschlossen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("abgeschlossen"));

        // Set lùi abgeschlossen → in Bearbeitung → 409 INVALID_STATUS_TRANSITION
        mockMvc.perform(patch("/bookings/" + past.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"in Bearbeitung\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Statuswechsel ist nicht erlaubt"));

        // Cancel lịch đã abgeschlossen (kể cả admin) → 409 BOOKING_ALREADY_COMPLETED
        mockMvc.perform(patch("/bookings/" + past.getId() + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Termin ist bereits abgeschlossen und kann nicht storniert werden"));

        // 10. PATCH /bookings/:id/cancel (chủ booking) → "storniert"
        mockMvc.perform(patch("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("storniert"));

        // 10c. Cancel 2 lần → 409 BOOKING_ALREADY_CANCELLED
        mockMvc.perform(patch("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Termin wurde bereits storniert"));

        // 10b. Chính sách 24h: đặt lịch vào slot hợp lệ kế tiếp (luôn < 24h) rồi cancel → 400 CANCEL_TOO_LATE
        LocalDateTime near = nextValidOpenSlot();
        String nearDate = near.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String nearTime = near.format(DateTimeFormatter.ofPattern("HH:mm"));
        String nearBody = "{\"serviceId\":" + serviceId + ",\"date\":\"" + nearDate + "\"," +
                "\"time\":\"" + nearTime + "\",\"name\":\"Anna Müller\",\"phone\":\"0176 12345678\"," +
                "\"email\":\"anna@test.de\"}";
        MvcResult nearResult = mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + userToken2)
                        .contentType(MediaType.APPLICATION_JSON).content(nearBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        long nearBookingId = getBody(nearResult).get("data").get("id").asLong();

        // Khách tự cancel trong vòng 24h → 400 với message tiếng Đức
        mockMvc.perform(patch("/bookings/" + nearBookingId + "/cancel")
                        .header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Stornierung nur bis 24 Stunden vor dem Termin möglich, bitte anrufen"));

        // Admin cancel lịch đó được (ngoại lệ chính sách)
        mockMvc.perform(patch("/bookings/" + nearBookingId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("storniert"));

        // 11. Token user thường bị chặn endpoint admin → 403
        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/stats/dashboard").header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isForbidden());

        // 12. Không token ở endpoint được bảo vệ → 401
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());

        // 13. IDOR: user thường không được xem booking qua legacy endpoint (chỉ ADMIN)
        mockMvc.perform(get("/booking/" + bookingId).header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/booking/user/" + anna.getId()).header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isForbidden());

        // 14. Đặt lịch ngoài giờ mở cửa (03:00) → 400 OUTSIDE_OPENING_HOURS
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":" + serviceId + ",\"date\":\"" + tomorrow + "\",\"time\":\"03:00\","
                                + "\"name\":\"Anna Müller\",\"phone\":\"0176 12345678\",\"email\":\"anna@test.de\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Termin muss zwischen 09:00 und 18:00 liegen"));
    }
}