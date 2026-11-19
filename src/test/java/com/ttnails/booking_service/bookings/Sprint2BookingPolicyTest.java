package com.ttnails.booking_service.bookings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.common.enums.Role;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.support.TestSupport;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 2 — state machine, huỷ theo policy, reschedule và no-show.
 *
 * <p>Bao phủ: S2-01 (history/terminal), S2-02 (cutoff + version), S2-03 (WARN/FEE
 * metadata), S2-05 (no-show chặn/override).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class Sprint2BookingPolicyTest {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ServiceRepository serviceRepository;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    String adminToken;
    String customerToken;
    Service service;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestSupport.adminToken(mockMvc, mapper);
        customerToken = TestSupport.userTokenWithRole(mockMvc, mapper, userRepository, passwordEncoder,
                "s2.policy.customer@test.de", Role.USER);
        service = serviceRepository.findByName("Nail-Art").orElseThrow();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bookingBody(long serviceId, LocalDate date, String time, String phone) {
        return "{\"serviceId\":" + serviceId + ",\"date\":\"" + date + "\",\"time\":\"" + time + "\"," +
                "\"name\":\"S2 Customer\",\"phone\":\"" + phone + "\",\"email\":\"s2.policy@test.de\"}";
    }

    private JsonNode createBooking(LocalDate date, String time, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(service.getId(), date, time, phone)))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("data");
    }

    /** Slot trống đầu tiên trong hôm nay hoặc ngày mai (luôn < 24h). */
    private LocalDate nearDate() throws Exception {
        LocalDate today = LocalDate.now();
        for (int i = 0; i <= 1; i++) {
            LocalDate date = today.plusDays(i);
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            MvcResult result = mockMvc.perform(get("/availability")
                            .param("date", date.toString())
                            .param("serviceIds", String.valueOf(service.getId())))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode any = body(result).get("data").get("anyAvailable");
            if (any != null && any.size() > 0) {
                return date;
            }
        }
        throw new AssertionError("Không có slot nào trong 24h tới để test");
    }

    private String nearTime(LocalDate date) throws Exception {
        MvcResult result = mockMvc.perform(get("/availability")
                        .param("date", date.toString())
                        .param("serviceIds", String.valueOf(service.getId())))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("data").get("anyAvailable").get(0).asText();
    }

    /**
     * Tìm ngày + giờ còn trống cho <b>đúng thợ</b> đang phụ trách booking.
     *
     * <p>Reschedule giữ nguyên thợ cũ, nên không thể hardcode giờ: các test khác
     * dùng chung H2 DB có thể đã chiếm slot đó, dẫn tới 409 `SLOT_TAKEN` (lỗi CI
     * từng gặp). Quét trong cửa sổ 14 ngày seed ca.</p>
     */
    private String[] firstFreeSlotForStaff(long staffId, int fromOffset) throws Exception {
        for (int offset = fromOffset; offset <= 13; offset++) {
            LocalDate date = TestSupport.workdayAfter(offset);
            MvcResult result = mockMvc.perform(get("/availability")
                            .param("date", date.toString())
                            .param("serviceIds", String.valueOf(service.getId()))
                            .param("staffId", String.valueOf(staffId)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode slots = body(result).get("data").get("anyAvailable");
            if (slots != null && slots.size() > 0) {
                return new String[]{date.toString(), slots.get(0).asText()};
            }
        }
        throw new AssertionError("Không còn slot trống cho thợ " + staffId);
    }

    @Test
    void createBookingStartsAtConfirmedWithConfirmationCodeAndHistory() throws Exception {
        LocalDate date = TestSupport.workdayAfter(3);
        JsonNode data = createBooking(date, "09:00", "01760000001");

        assertThat(data.get("status").asText()).isEqualTo("bestätigt");
        assertThat(data.get("confirmationCode").asText()).startsWith("TT-");

        mockMvc.perform(get("/bookings/" + data.get("id").asLong() + "/history")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.data[0].toStatus").value("bestätigt"));
    }

    @Test
    void freeCancelRecordsLateFlagFalseAndTerminalIsImmutable() throws Exception {
        LocalDate date = TestSupport.workdayAfter(4);
        long id = createBooking(date, "10:00", "01760000002").get("id").asLong();

        mockMvc.perform(patch("/bookings/" + id + "/cancel")
                        .header("Authorization", "Bearer " + customerToken)
                        .param("reason", "Urlaub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("storniert"))
                .andExpect(jsonPath("$.data.cancellation.late").value(false));

        mockMvc.perform(get("/bookings/" + id + "/history")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].toStatus").value("storniert"))
                .andExpect(jsonPath("$.data[1].reason").value("Urlaub"));

        // Terminal bất biến: cancel lần 2 và đổi status đều bị chặn
        mockMvc.perform(patch("/bookings/" + id + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_ALREADY_CANCELLED"));

        mockMvc.perform(patch("/bookings/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"abgeschlossen\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void lateCancelWarnsButStillSucceeds() throws Exception {
        LocalDate date = nearDate();
        String time = nearTime(date);
        long id = createBooking(date, time, "01760000003").get("id").asLong();

        mockMvc.perform(patch("/bookings/" + id + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("storniert"))
                .andExpect(jsonPath("$.data.cancellation.late").value(true));
    }

    @Test
    void checkInBeforeWindowIsRejected() throws Exception {
        long id = createBooking(TestSupport.workdayAfter(5), "09:00", "01760000004").get("id").asLong();

        mockMvc.perform(patch("/bookings/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"eingecheckt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CHECK_IN_TOO_EARLY"));
    }

    @Test
    void rescheduleWithinCutoffNeedsStaffOverrideWithReason() throws Exception {
        LocalDate date = nearDate();
        String time = nearTime(date);
        JsonNode created = createBooking(date, time, "01760000005");
        long id = created.get("id").asLong();
        long staffId = created.get("staffId").asLong();

        // Reschedule giữ nguyên thợ cũ → phải hỏi slot trống của đúng thợ đó.
        String[] target = firstFreeSlotForStaff(staffId, 6);
        String body = "{\"date\":\"" + target[0] + "\",\"time\":\"" + target[1] + "\"}";

        mockMvc.perform(patch("/bookings/" + id + "/reschedule")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESCHEDULE_TOO_LATE"));

        mockMvc.perform(patch("/bookings/" + id + "/reschedule")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REASON_REQUIRED"));

        mockMvc.perform(patch("/bookings/" + id + "/reschedule")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + target[0] + "\",\"time\":\"" + target[1] +
                                "\",\"reason\":\"Kulanz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(
                        LocalDate.parse(target[0]).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))))
                .andExpect(jsonPath("$.data.versionNo").value(1));
    }

    @Test
    void customerCanRescheduleFarFutureAndVersionIsStored() throws Exception {
        JsonNode created = createBooking(TestSupport.workdayAfter(7), "13:00", "01760000006");
        long id = created.get("id").asLong();
        long staffId = created.get("staffId").asLong();

        String[] target = firstFreeSlotForStaff(staffId, 9);

        mockMvc.perform(patch("/bookings/" + id + "/reschedule")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + target[0] + "\",\"time\":\"" + target[1] + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.time").value(target[1]))
                .andExpect(jsonPath("$.data.versionNo").value(1));
    }

    @Test
    void noShowRequiresStartOrExplicitOverride() throws Exception {
        long id = createBooking(TestSupport.workdayAfter(8), "10:00", "01760000007").get("id").asLong();

        mockMvc.perform(patch("/bookings/" + id + "/no-show")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_SHOW_TOO_EARLY"));

        mockMvc.perform(patch("/bookings/" + id + "/no-show")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"override\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REASON_REQUIRED"));

        mockMvc.perform(patch("/bookings/" + id + "/no-show")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"override\":true,\"reason\":\"Telefonisch bestätigt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("nicht erschienen"));

        // Idempotent khi đánh dấu lại
        mockMvc.perform(patch("/bookings/" + id + "/no-show")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("nicht erschienen"));
    }

    @Test
    void tooManyNoShowsBlockNewBookingUntilStaffOverrides() throws Exception {
        String phone = "01769990001";
        for (int i = 1; i <= 3; i++) {
            Booking past = new Booking();
            past.setFirstName("No Show");
            past.setPhone(phone);
            past.setEmail("noshow@test.de");
            past.setService(service.getName());
            past.setStartAt(LocalDateTime.now().minusDays(i).withHour(10).withMinute(0)
                    .withSecond(0).withNano(0));
            past.setPreferredDateTime(past.getStartAt());
            past.setStatus("nicht erschienen");
            bookingRepository.save(past);
        }
        LocalDate date = TestSupport.workdayAfter(10);
        String body = bookingBody(service.getId(), date, "15:00", phone);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_SHOW_BLOCKED"));

        String overrideBody = "{\"serviceId\":" + service.getId() + ",\"date\":\"" + date + "\"," +
                "\"time\":\"15:00\",\"name\":\"No Show\",\"phone\":\"" + phone + "\"," +
                "\"email\":\"noshow@test.de\",\"overrideNoShow\":true}";
        mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("bestätigt"));
    }

    @Test
    void cannotCompleteBeforeStart() throws Exception {
        long id = createBooking(TestSupport.workdayAfter(11), "16:00", "01760000008").get("id").asLong();

        mockMvc.perform(patch("/bookings/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"abgeschlossen\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMPLETION_TOO_EARLY"));
    }

    @Test
    void completedBookingIsTerminal() throws Exception {
        // Seed lịch quá khứ để có thể đóng sổ
        Booking past = new Booking();
        past.setFirstName("Past");
        past.setPhone("01760000009");
        past.setEmail("past@test.de");
        past.setService(service.getName());
        past.setStartAt(LocalDateTime.now().minusHours(3).withSecond(0).withNano(0));
        past.setPreferredDateTime(past.getStartAt());
        past.setStatus("bestätigt");
        past = bookingRepository.save(past);

        mockMvc.perform(patch("/bookings/" + past.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"abgeschlossen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("abgeschlossen"));

        mockMvc.perform(patch("/bookings/" + past.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"bestätigt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }
}
