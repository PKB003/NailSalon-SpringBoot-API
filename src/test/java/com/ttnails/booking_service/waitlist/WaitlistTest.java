package com.ttnails.booking_service.waitlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.support.TestSupport;
import com.ttnails.booking_service.waitlist.entity.WaitlistEntry;
import com.ttnails.booking_service.waitlist.entity.WaitlistStatus;
import com.ttnails.booking_service.waitlist.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S2-04 — waitlist: chống spam, giải phóng slot thì notify, khách tự huỷ. */
@SpringBootTest
@AutoConfigureMockMvc
class WaitlistTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ServiceRepository serviceRepository;
    @Autowired
    WaitlistRepository waitlistRepository;

    String adminToken;
    Service service;
    LocalDate date;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestSupport.adminToken(mockMvc, mapper);
        service = serviceRepository.findByName("Nail-Art").orElseThrow();
        date = TestSupport.workdayAfter(8);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String waitlistBody(String phone) {
        return "{\"serviceId\":" + service.getId() + ",\"desiredDate\":\"" + date + "\"," +
                "\"phone\":\"" + phone + "\",\"name\":\"Warteliste\",\"email\":\"wait@test.de\"}";
    }

    private long createBooking(String phone) throws Exception {
        String request = "{\"serviceId\":" + service.getId() + ",\"date\":\"" + date + "\"," +
                "\"time\":\"09:00\",\"name\":\"Warteliste\",\"phone\":\"" + phone + "\"," +
                "\"email\":\"wait@test.de\"}";
        MvcResult result = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("data").get("id").asLong();
    }

    @Test
    void duplicateWaitlistEntryIsRejected() throws Exception {
        mockMvc.perform(post("/waitlist")
                        .contentType(MediaType.APPLICATION_JSON).content(waitlistBody("01763330001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/waitlist")
                        .contentType(MediaType.APPLICATION_JSON).content(waitlistBody("01763330001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WAITLIST_DUPLICATE"));
    }

    @Test
    void cancelledBookingNotifiesActiveWaitlistEntry() throws Exception {
        String phone = "01763330002";
        long bookingId = createBooking(phone);

        MvcResult created = mockMvc.perform(post("/waitlist")
                        .contentType(MediaType.APPLICATION_JSON).content(waitlistBody(phone)))
                .andExpect(status().isOk())
                .andReturn();
        long waitlistId = body(created).get("data").get("id").asLong();

        // Huỷ booking → slot giải phóng → entry được đánh dấu NOTIFIED
        mockMvc.perform(patch("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("reason", "Test waitlist"))
                .andExpect(status().isOk());

        WaitlistEntry entry = waitlistRepository.findById(waitlistId).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.NOTIFIED);
        assertThat(entry.getNotifiedAt()).isNotNull();
        assertThat(entry.getActiveKey()).isNull();

        // Staff thấy entry qua API filter
        mockMvc.perform(get("/waitlist")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("date", date.toString())
                        .param("serviceId", String.valueOf(service.getId()))
                        .param("status", WaitlistStatus.NOTIFIED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].status").value(WaitlistStatus.NOTIFIED));

        // Khách tự huỷ bằng SĐT
        mockMvc.perform(delete("/waitlist/" + waitlistId).param("phone", phone))
                .andExpect(status().isOk());
        assertThat(waitlistRepository.findById(waitlistId).orElseThrow().getStatus())
                .isEqualTo(WaitlistStatus.CANCELLED);
    }

    @Test
    void waitlistIsPublicButStaffListRequiresAuth() throws Exception {
        mockMvc.perform(get("/waitlist")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/waitlist")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
