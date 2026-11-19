package com.ttnails.booking_service.bookings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S2-06 audit log + S2-09 pagination/filter/sort cho admin list. */
@SpringBootTest
@AutoConfigureMockMvc
class Sprint2AdminApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ServiceRepository serviceRepository;
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
                "s2.admin.customer@test.de", Role.USER);
        service = serviceRepository.findByName("Nail-Art").orElseThrow();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private long createBooking(LocalDate date, String phone) throws Exception {
        String request = "{\"serviceId\":" + service.getId() + ",\"date\":\"" + date + "\"," +
                "\"time\":\"09:00\",\"name\":\"Admin API\",\"phone\":\"" + phone + "\"," +
                "\"email\":\"adminapi@test.de\"}";
        MvcResult result = mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("data").get("id").asLong();
    }

    @Test
    void adminCancelWritesAuditLogVisibleOnlyToAdmin() throws Exception {
        long id = createBooking(TestSupport.workdayAfter(5), "01764440001");

        mockMvc.perform(patch("/bookings/" + id + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("reason", "Audit Test"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("entityType", "BOOKING")
                        .param("entityId", String.valueOf(id))
                        .param("action", "BOOKING_CANCEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].action").value("BOOKING_CANCEL"))
                .andExpect(jsonPath("$.data.content[0].entityType").value("BOOKING"))
                .andExpect(jsonPath("$.data.content[0].entityId").value(String.valueOf(id)))
                .andExpect(jsonPath("$.data.content[0].reason").value("Audit Test"))
                .andExpect(jsonPath("$.data.content[0].actorEmail").isNotEmpty());

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingListSupportsPagingAndFilters() throws Exception {
        String phone = "01764440002";
        long cancelledId = createBooking(TestSupport.workdayAfter(6), phone);
        mockMvc.perform(patch("/bookings/" + cancelledId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Không có page → array cũ
        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // Có page → PageResponse
        mockMvc.perform(get("/bookings")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "startAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").isNumber());

        // Filter theo SĐT + status
        mockMvc.perform(get("/bookings")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("q", phone)
                        .param("status", "storniert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value((int) cancelledId))
                .andExpect(jsonPath("$.data.content[0].status").value("storniert"));
    }

    @Test
    void customerListSupportsPagingAndNoShowCount() throws Exception {
        mockMvc.perform(get("/customers").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").isNumber());

        mockMvc.perform(get("/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.email == 's2.admin.customer@test.de')].noShowCount")
                        .isArray());
    }
}
