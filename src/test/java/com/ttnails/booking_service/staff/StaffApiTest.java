package com.ttnails.booking_service.staff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1-02 — integration test cho CRUD thợ, kỹ năng, ca làm, nghỉ phép.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ServiceRepository serviceRepository;

    String adminToken;
    Long serviceId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestSupport.adminToken(mockMvc, mapper);
        serviceId = serviceRepository.findByActiveTrueOrderByIdAsc().get(0).getId();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private long createStaff(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + name + "\",\"color\":\"#123456\"," +
                                "\"commissionRate\":0.35," +
                                "\"skills\":[{\"serviceId\":" + serviceId + ",\"level\":\"EXPERT\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.displayName").value(name))
                .andExpect(jsonPath("$.data.skills[0].serviceId").value(serviceId.intValue()))
                .andExpect(jsonPath("$.data.skills[0].level").value("EXPERT"))
                .andReturn();
        return body(result).get("data").get("id").asLong();
    }

    // ==================== Staff ====================

    @Test
    void listReturnsSeededStaffWithSkills() throws Exception {
        mockMvc.perform(get("/staff").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].displayName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].skills").isArray())
                .andExpect(jsonPath("$.data[0].skills.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void createReadUpdateStaff() throws Exception {
        long id = createStaff("Test Thợ A");

        mockMvc.perform(get("/staff/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value((int) id))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.branchId").isNumber());

        mockMvc.perform(put("/staff/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Test Thợ B\",\"color\":\"#ABCDEF\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Test Thợ B"))
                .andExpect(jsonPath("$.data.color").value("#ABCDEF"))
                // PUT không gửi skills → giữ nguyên kỹ năng cũ
                .andExpect(jsonPath("$.data.skills.length()").value(1));
    }

    @Test
    void replaceSkillsKeepsOnlyGivenServices() throws Exception {
        long id = createStaff("Test Thợ Skills");
        Long secondService = serviceRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(s -> s.getId()).filter(sid -> !sid.equals(serviceId)).findFirst().orElseThrow();

        mockMvc.perform(put("/staff/" + id + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"serviceId\":" + secondService + ",\"level\":\"BASIC\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].serviceId").value(secondService.intValue()))
                .andExpect(jsonPath("$.data[0].level").value("BASIC"));

        mockMvc.perform(get("/staff/" + id + "/skills").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].serviceId").value(secondService.intValue()));
    }

    @Test
    void deactivateHidesStaffFromActiveList() throws Exception {
        long id = createStaff("Test Thợ Nghỉ");

        mockMvc.perform(delete("/staff/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        JsonNode active = body(mockMvc.perform(get("/staff").header("Authorization", "Bearer " + adminToken))
                .andReturn());
        List<Long> ids = new ArrayList<>();
        active.get("data").forEach(node -> ids.add(node.get("id").asLong()));
        assertThat(ids).doesNotContain(id);

        mockMvc.perform(get("/staff").param("includeInactive", "true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].active").value(false));
    }

    @Test
    void createStaffValidationAndNotFound() throws Exception {
        mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\",\"commissionRate\":2.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/staff/999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // ==================== Shifts ====================

    @Test
    void addShiftListAndRejectOverlap() throws Exception {
        long id = createStaff("Test Thợ Ca");
        String date = TestSupport.workdayAfter(3).toString();

        MvcResult created = mockMvc.perform(post("/staff/" + id + "/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"" + date + "\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.startTime").value("09:00"))
                .andExpect(jsonPath("$.data.endTime").value("12:00"))
                .andReturn();
        long shiftId = body(created).get("data").get("id").asLong();

        mockMvc.perform(post("/staff/" + id + "/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"" + date + "\",\"startTime\":\"11:00\",\"endTime\":\"14:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SHIFT_OVERLAP"));

        mockMvc.perform(get("/staff/" + id + "/shifts")
                        .param("from", date).param("to", date)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].workDate").value(date));

        mockMvc.perform(delete("/staff/" + id + "/shifts/" + shiftId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/staff/" + id + "/shifts").param("from", date).param("to", date)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void shiftRequiresStartBeforeEnd() throws Exception {
        long id = createStaff("Test Thợ Ca Lỗi");
        mockMvc.perform(post("/staff/" + id + "/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"" + TestSupport.workdayAfter(3) + "\"," +
                                "\"startTime\":\"14:00\",\"endTime\":\"12:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"));
    }

    // ==================== Time off ====================

    @Test
    void createTimeOffRejectOverlapAndCancel() throws Exception {
        long id = createStaff("Test Thợ Nghỉ Phép");
        LocalDate date = TestSupport.workdayAfter(4);

        MvcResult created = mockMvc.perform(post("/staff/" + id + "/time-off")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + date + "T09:00:00\",\"endAt\":\"" + date +
                                "T12:00:00\",\"reason\":\"Arzttermin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.reason").value("Arzttermin"))
                .andReturn();
        long timeOffId = body(created).get("data").get("id").asLong();

        mockMvc.perform(post("/staff/" + id + "/time-off")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + date + "T11:00:00\",\"endAt\":\"" + date + "T13:00:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TIME_OFF_OVERLAP"));

        mockMvc.perform(get("/staff/" + id + "/time-off").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/staff/" + id + "/time-off/" + timeOffId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/staff/" + id + "/time-off").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("CANCELLED"));

        // Đã huỷ → được phép đăng ký lại cùng khoảng giờ
        mockMvc.perform(post("/staff/" + id + "/time-off")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + date + "T11:00:00\",\"endAt\":\"" + date + "T13:00:00\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void timeOffConflictingWithBookingIsRejected() throws Exception {
        long id = createStaff("Test Thợ Có Lịch");
        LocalDate date = TestSupport.workdayAfter(5);
        TestSupport.addFullDayShift(mockMvc, adminToken, id, date);

        MvcResult booking = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":" + serviceId + ",\"staffId\":" + id + "," +
                                "\"date\":\"" + date + "\",\"time\":\"10:00\"," +
                                "\"name\":\"Time Off Test\",\"phone\":\"0176123456\",\"email\":\"toff@test.de\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(booking).get("data").get("staffId").asLong()).isEqualTo(id);

        mockMvc.perform(post("/staff/" + id + "/time-off")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + date + "T09:30:00\",\"endAt\":\"" + date + "T11:00:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TIME_OFF_CONFLICTS_BOOKING"));
    }

    // ==================== Bookings của thợ ====================

    @Test
    void staffBookingsEndpointReturnsOnlyThatStaff() throws Exception {
        long id = createStaff("Test Thợ Lịch");
        LocalDate date = TestSupport.workdayAfter(6);
        TestSupport.addFullDayShift(mockMvc, adminToken, id, date);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[" + serviceId + "],\"staffId\":" + id + "," +
                                "\"date\":\"" + date + "\",\"time\":\"11:00\"," +
                                "\"name\":\"Staff Booking\",\"phone\":\"0176987654\",\"email\":\"sb@test.de\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/staff/" + id + "/bookings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].staffId").value((int) id))
                .andExpect(jsonPath("$.data[0].date").value(
                        date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))));
    }
}
