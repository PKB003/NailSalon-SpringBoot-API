package com.ttnails.booking_service.availability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.support.TestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1-04 — integration test cho {@code GET /availability}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AvailabilityApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ServiceRepository serviceRepository;

    String adminToken;
    Service manikuere;
    Service pedikuere;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestSupport.adminToken(mockMvc, mapper);
        manikuere = serviceRepository.findByName("Maniküre").orElseThrow();
        pedikuere = serviceRepository.findByName("Pediküre").orElseThrow();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private long createStaff(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + name + "\",\"skills\":[" +
                                "{\"serviceId\":" + manikuere.getId() + "}," +
                                "{\"serviceId\":" + pedikuere.getId() + "}]}"))
                .andExpect(status().isOk())
                .andReturn();
        long staffId = body(result).get("data").get("id").asLong();
        TestSupport.addFullDayShiftsForTwoWeeks(mockMvc, adminToken, staffId);
        return staffId;
    }

    @Test
    void availabilityIsPublicAndReturnsStaffSlots() throws Exception {
        String date = TestSupport.workdayAfter(1).toString();

        MvcResult result = mockMvc.perform(get("/availability")
                        .param("date", date)
                        .param("serviceIds", manikuere.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(date))
                .andExpect(jsonPath("$.data.timezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.data.slotStepMinutes").value(30))
                .andExpect(jsonPath("$.data.totalDurationMinutes").value(45))
                .andExpect(jsonPath("$.data.anyAvailable[0]").value("09:00"))
                .andExpect(jsonPath("$.data.staff").isArray())
                .andExpect(jsonPath("$.data.staff[0].staffId").isNumber())
                .andExpect(jsonPath("$.data.staff[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data.staff[0].slots[0].start").value("09:00"))
                .andExpect(jsonPath("$.data.staff[0].slots[0].end").value("09:45"))
                .andReturn();

        assertThat(body(result).get("data").get("staff").size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void multiServiceBlocksFullSpan() throws Exception {
        mockMvc.perform(get("/availability")
                        .param("date", TestSupport.workdayAfter(1).toString())
                        .param("serviceIds", manikuere.getId().toString(), pedikuere.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDurationMinutes").value(105))
                .andExpect(jsonPath("$.data.anyAvailable[0]").value("09:00"));
    }

    @Test
    void staffFilterReturnsOnlyRequestedStaff() throws Exception {
        long staffId = createStaff("Test AVA Filter");

        mockMvc.perform(get("/availability")
                        .param("date", TestSupport.workdayAfter(2).toString())
                        .param("serviceIds", manikuere.getId().toString())
                        .param("staffId", String.valueOf(staffId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staff.length()").value(1))
                .andExpect(jsonPath("$.data.staff[0].staffId").value((int) staffId));
    }

    @Test
    void unknownStaffReturnsNotFound() throws Exception {
        mockMvc.perform(get("/availability")
                        .param("date", TestSupport.workdayAfter(2).toString())
                        .param("serviceIds", manikuere.getId().toString())
                        .param("staffId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tooManyServicesIsRejected() throws Exception {
        List<Long> ids = serviceRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(Service::getId).limit(4).toList();
        assertThat(ids).hasSize(4);

        mockMvc.perform(get("/availability")
                        .param("date", TestSupport.workdayAfter(1).toString())
                        .param("serviceIds", ids.stream().map(String::valueOf).toArray(String[]::new)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_SERVICES"));
    }

    @Test
    void unknownOrMissingServiceIsRejected() throws Exception {
        mockMvc.perform(get("/availability")
                        .param("date", TestSupport.workdayAfter(1).toString())
                        .param("serviceIds", "999999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/availability")
                        .param("date", TestSupport.workdayAfter(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void malformedDateIsRejected() throws Exception {
        mockMvc.perform(get("/availability")
                        .param("date", "20-11-2026")
                        .param("serviceIds", manikuere.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE"));
    }

    @Test
    void sundayHasNoSlots() throws Exception {
        LocalDate sunday = TestSupport.nextSunday();
        Assumptions.assumeTrue(sunday != null, "Không có Chủ nhật trong 14 ngày seed");

        mockMvc.perform(get("/availability")
                        .param("date", sunday.toString())
                        .param("serviceIds", manikuere.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.anyAvailable.length()").value(0))
                .andExpect(jsonPath("$.data.staff.length()").value(0));
    }

    @Test
    void timeOffRemovesSlotsFromThatStaff() throws Exception {
        long staffId = createStaff("Test AVA Nghỉ");
        LocalDate date = TestSupport.workdayAfter(3);

        mockMvc.perform(post("/staff/" + staffId + "/time-off")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + date + "T09:00:00\",\"endAt\":\"" + date + "T12:00:00\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/availability")
                        .param("date", date.toString())
                        .param("serviceIds", manikuere.getId().toString())
                        .param("staffId", String.valueOf(staffId)))
                .andExpect(status().isOk())
                .andReturn();

        List<String> starts = new ArrayList<>();
        body(result).get("data").get("anyAvailable").forEach(node -> starts.add(node.asText()));
        assertThat(starts).isNotEmpty();
        assertThat(starts).doesNotContain("09:00", "09:30", "10:00", "10:30", "11:00", "11:30");
        assertThat(starts).contains("12:00");
    }

    @Test
    void existingBookingRemovesSlot() throws Exception {
        long staffId = createStaff("Test AVA Booking");
        LocalDate date = TestSupport.workdayAfter(4);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":" + manikuere.getId() + ",\"staffId\":" + staffId + "," +
                                "\"date\":\"" + date + "\",\"time\":\"10:00\"," +
                                "\"name\":\"AVA Booking\",\"phone\":\"0176000111\",\"email\":\"ava@test.de\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/availability")
                        .param("date", date.toString())
                        .param("serviceIds", manikuere.getId().toString())
                        .param("staffId", String.valueOf(staffId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = body(result).get("data");
        List<String> starts = new ArrayList<>();
        data.get("anyAvailable").forEach(node -> starts.add(node.asText()));

        // Booking 10:00–10:45 + buffer 10' → chặn 10:00, 10:30; 11:00 vẫn trống
        assertThat(starts).doesNotContain("10:00", "10:30");
        assertThat(starts).contains("11:00");
    }
}
