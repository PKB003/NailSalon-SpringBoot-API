package com.ttnails.booking_service.bookings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S2-07 — Idempotency-Key cho POST /bookings. */
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ServiceRepository serviceRepository;
    @Autowired
    BookingRepository bookingRepository;

    Service service;

    @BeforeEach
    void setUp() {
        service = serviceRepository.findByName("Nail-Art").orElseThrow();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bookingBody(String date, String time, String phone) {
        return "{\"serviceId\":" + service.getId() + ",\"date\":\"" + date + "\"," +
                "\"time\":\"" + time + "\",\"name\":\"Idem Test\",\"phone\":\"" + phone + "\"," +
                "\"email\":\"idem@test.de\"}";
    }

    @Test
    void retryWithSameKeyReturnsSameBooking() throws Exception {
        String key = UUID.randomUUID().toString();
        String request = bookingBody(TestSupport.workdayAfter(3).toString(), "09:00", "01762220001");

        MvcResult first = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andReturn();
        long firstId = body(first).get("data").get("id").asLong();

        MvcResult second = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andReturn();
        long secondId = body(second).get("data").get("id").asLong();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(bookingRepository.findByPhoneOrderByStartAtDesc("01762220001")).hasSize(1);
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = bookingBody(TestSupport.workdayAfter(4).toString(), "10:00", "01762220002");
        String different = bookingBody(TestSupport.workdayAfter(5).toString(), "10:00", "01762220002");

        mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(different))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(bookingRepository.findByPhoneOrderByStartAtDesc("01762220002")).hasSize(1);
    }

    @Test
    void withoutKeyRetriesCreateSeparateBookings() throws Exception {
        String first = bookingBody(TestSupport.workdayAfter(6).toString(), "11:00", "01762220003");
        String second = bookingBody(TestSupport.workdayAfter(7).toString(), "11:00", "01762220003");

        long firstId = body(mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isOk()).andReturn()).get("data").get("id").asLong();
        long secondId = body(mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isOk()).andReturn()).get("data").get("id").asLong();

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(bookingRepository.findByPhoneOrderByStartAtDesc("01762220003")).hasSize(2);
    }
}
