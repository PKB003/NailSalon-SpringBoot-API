package com.ttnails.booking_service.bookings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.support.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1-05/S1-06 — luồng đặt lịch mới: chọn thợ, multi-service liên tiếp,
 * snapshot giá và chặn trùng lịch.
 *
 * <p>Demo bắt buộc: đặt 2 dịch vụ liên tiếp (60' + 45') với một thợ cụ thể.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ServiceRepository serviceRepository;

    String adminToken;
    Service pedikuere;   // 60'
    Service manikuere;   // 45'

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestSupport.adminToken(mockMvc, mapper);
        pedikuere = serviceRepository.findByName("Pediküre").orElseThrow();
        manikuere = serviceRepository.findByName("Maniküre").orElseThrow();
    }

    private JsonNode body(MvcResult result) throws Exception {
        // MockHttpServletResponse.getContentAsString() mặc định ISO-8859-1 → phải decode UTF-8
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private long createStaff(String name, List<Long> serviceIds) throws Exception {
        String skills = serviceIds.stream()
                .map(id -> "{\"serviceId\":" + id + "}")
                .reduce((a, b) -> a + "," + b).orElse("");
        MvcResult result = mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + name + "\",\"skills\":[" + skills + "]}"))
                .andExpect(status().isOk())
                .andReturn();
        long staffId = body(result).get("data").get("id").asLong();
        TestSupport.addFullDayShiftsForTwoWeeks(mockMvc, adminToken, staffId);
        return staffId;
    }

    private String bookingBody(List<Long> serviceIds, Long staffId, LocalDate date, String time) {
        String ids = serviceIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        return "{\"serviceIds\":[" + ids + "]" +
                (staffId != null ? ",\"staffId\":" + staffId : "") +
                ",\"date\":\"" + date + "\",\"time\":\"" + time + "\"," +
                "\"name\":\"Anna Müller\",\"phone\":\"0176123456\",\"email\":\"flow@test.de\"}";
    }

    @Test
    void bookTwoConsecutiveServicesWithChosenStaff() throws Exception {
        // Tên ASCII: MockMvc mặc định ISO-8859-1 nên ký tự có dấu trong JSON body sẽ bị mojibake.
        long staffId = createStaff("Flow Tech 60+45",
                List.of(pedikuere.getId(), manikuere.getId()));
        LocalDate date = TestSupport.workdayAfter(7);

        MvcResult result = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(pedikuere.getId(), manikuere.getId()),
                                staffId, date, "09:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.staffId").value((int) staffId))
                .andExpect(jsonPath("$.data.staffName").value("Flow Tech 60+45"))
                .andExpect(jsonPath("$.data.time").value("09:00"))
                // end = start + Σduration (60 + 45), không gồm buffer 10'
                .andExpect(jsonPath("$.data.endTime").value("10:45"))
                .andExpect(jsonPath("$.data.totalDurationMinutes").value(105))
                .andExpect(jsonPath("$.data.currency").value("EUR"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].serviceId").value(pedikuere.getId().intValue()))
                .andExpect(jsonPath("$.data.items[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.data.items[0].endTime").value("10:00"))
                .andExpect(jsonPath("$.data.items[1].serviceId").value(manikuere.getId().intValue()))
                .andExpect(jsonPath("$.data.items[1].startTime").value("10:00"))
                .andExpect(jsonPath("$.data.items[1].endTime").value("10:45"))
                .andExpect(jsonPath("$.data.items[1].staffId").value((int) staffId))
                .andReturn();

        JsonNode data = body(result).get("data");
        // Snapshot giá: 35 + 28 = 63 EUR (lấy tại thời điểm đặt)
        assertThat(data.get("priceSnapshot").decimalValue())
                .isEqualByComparingTo(java.math.BigDecimal.valueOf(
                        pedikuere.getPrice() + manikuere.getPrice()));
        assertThat(data.get("employee").asText()).isEqualTo("Flow Tech 60+45");
        assertThat(data.get("service").asText()).contains("Pediküre").contains("Maniküre");
    }

    @Test
    void secondBookingCannotOverlapSameStaff() throws Exception {
        long staffId = createStaff("Flow Tech Trùng", List.of(manikuere.getId()));
        LocalDate date = TestSupport.workdayAfter(8);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(manikuere.getId()), staffId, date, "10:00")))
                .andExpect(status().isOk());

        // 10:30 rơi vào buffer của booking trước (10:00–10:45 + 10')
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(manikuere.getId()), staffId, date, "10:30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SLOT_TAKEN"));

        // 11:00 đã qua buffer → thành công
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(manikuere.getId()), staffId, date, "11:00")))
                .andExpect(status().isOk());
    }

    @Test
    void sameStartTimeIsRejectedByOverlapCheck() throws Exception {
        long staffId = createStaff("Flow Tech Cùng Giờ", List.of(manikuere.getId()));
        LocalDate date = TestSupport.workdayAfter(9);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(manikuere.getId()), staffId, date, "13:00")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(manikuere.getId()), staffId, date, "13:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SLOT_TAKEN"));
    }

    @Test
    void staffWithoutSkillCannotTakeBooking() throws Exception {
        long staffId = createStaff("Flow Tech Không Skill", List.of(manikuere.getId()));
        LocalDate date = TestSupport.workdayAfter(10);

        // Pediküre chưa được gán skill cho thợ này → 409
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(pedikuere.getId()), staffId, date, "09:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_STAFF_AVAILABLE"));
    }

    @Test
    void autoAssignPicksLeastBusyStaff() throws Exception {
        LocalDate date = TestSupport.workdayAfter(11);
        // Dùng dịch vụ 30' để còn nhiều slot; thợ seed đều có skill mọi dịch vụ
        Service nailArt = serviceRepository.findByName("Nail-Art").orElseThrow();

        MvcResult first = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(nailArt.getId()), null, date, "09:00")))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(nailArt.getId()), null, date, "09:00")))
                .andExpect(status().isOk())
                .andReturn();

        long firstStaff = body(first).get("data").get("staffId").asLong();
        long secondStaff = body(second).get("data").get("staffId").asLong();
        assertThat(secondStaff)
                .as("lần đặt thứ hai phải chọn thợ khác vì thợ đầu đã bận")
                .isNotEqualTo(firstStaff);
    }

    @Test
    void outsideOpeningHoursAndTooManyServicesAreRejected() throws Exception {
        LocalDate date = TestSupport.workdayAfter(1);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(manikuere.getId()), null, date, "03:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OUTSIDE_OPENING_HOURS"));

        List<Long> four = serviceRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(Service::getId).limit(4).toList();
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(four, null, date, "09:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_SERVICES"));
    }

    @Test
    void unknownServiceIsRejected() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(List.of(999999L), null, TestSupport.workdayAfter(1), "09:00")))
                .andExpect(status().isNotFound());
    }
}
