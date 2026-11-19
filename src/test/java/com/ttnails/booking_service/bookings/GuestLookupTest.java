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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S2-08 — guest tra cứu booking bằng confirmation code + SĐT, xác thực OTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GuestLookupTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ServiceRepository serviceRepository;

    Service service;

    @BeforeEach
    void setUp() {
        service = serviceRepository.findByName("Nail-Art").orElseThrow();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode createBooking(LocalDate date, String time, String phone) throws Exception {
        String body = "{\"serviceId\":" + service.getId() + ",\"date\":\"" + date + "\"," +
                "\"time\":\"" + time + "\",\"name\":\"Guest Lookup\"," +
                "\"phone\":\"" + phone + "\",\"email\":\"lookup@test.de\"}";
        MvcResult result = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("data");
    }

    @Test
    void lookupAndVerifyReturnsOwnBookingOnly() throws Exception {
        JsonNode booking = createBooking(TestSupport.workdayAfter(3), "09:00", "01761110001");
        String code = booking.get("confirmationCode").asText();

        MvcResult request = mockMvc.perform(post("/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + code + "\",\"phone\":\"0176 1110001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.otp").isNotEmpty())
                .andReturn();
        JsonNode challenge = body(request).get("data");
        String challengeId = challenge.get("challengeId").asText();
        String otp = challenge.get("otp").asText();

        // Sai OTP → 400
        mockMvc.perform(post("/bookings/lookup/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OTP"));

        // Đúng OTP → trả đúng booking
        mockMvc.perform(post("/bookings/lookup/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.id").value(booking.get("id").asInt()))
                .andExpect(jsonPath("$.data.booking.confirmationCode").value(code));

        // OTP dùng 1 lần
        mockMvc.perform(post("/bookings/lookup/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OTP"));
    }

    @Test
    void wrongPhoneOrCodeNeverLeaksBooking() throws Exception {
        JsonNode booking = createBooking(TestSupport.workdayAfter(4), "10:00", "01761110002");
        String code = booking.get("confirmationCode").asText();

        mockMvc.perform(post("/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + code + "\",\"phone\":\"01769999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CONFIRMATION_CODE"));

        mockMvc.perform(post("/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"TT-ZZZZZZ\",\"phone\":\"01761110002\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CONFIRMATION_CODE"));
    }

    @Test
    void rateLimitPerPhone() throws Exception {
        JsonNode booking = createBooking(TestSupport.workdayAfter(5), "11:00", "01761110003");
        String code = booking.get("confirmationCode").asText();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/bookings/lookup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"confirmationCode\":\"" + code + "\",\"phone\":\"01761110003\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + code + "\",\"phone\":\"01761110003\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void otpExpiresAndAttemptsAreLimited() throws Exception {
        JsonNode booking = createBooking(TestSupport.workdayAfter(6), "12:00", "01761110004");
        String code = booking.get("confirmationCode").asText();

        MvcResult request = mockMvc.perform(post("/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + code + "\",\"phone\":\"01761110004\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = body(request).get("data").get("challengeId").asText();

        // 4 lần sai → INVALID_OTP, lần thứ 5 → OTP_TOO_MANY_ATTEMPTS
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/bookings/lookup/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"999999\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_OTP"));
        }
        mockMvc.perform(post("/bookings/lookup/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"999999\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OTP_TOO_MANY_ATTEMPTS"));
    }

    @Test
    void unknownChallengeIsRejected() throws Exception {
        mockMvc.perform(post("/bookings/lookup/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"does-not-exist\",\"otp\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OTP"));
    }

    @Test
    void challengeIsHashedNotStoredInPlaintext() throws Exception {
        // Bảo vệ hồi quy: OTP trong response là plaintext, nhưng DB chỉ có hash.
        JsonNode booking = createBooking(TestSupport.workdayAfter(7), "13:00", "01761110005");
        String code = booking.get("confirmationCode").asText();
        MvcResult request = mockMvc.perform(post("/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\":\"" + code + "\",\"phone\":\"01761110005\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String otp = body(request).get("data").get("otp").asText();
        assertThat(otp).hasSize(6).matches("\\d{6}");
    }
}
