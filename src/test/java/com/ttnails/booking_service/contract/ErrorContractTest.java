package com.ttnails.booking_service.contract;

import com.ttnails.booking_service.common.enums.Role;
import com.ttnails.booking_service.security.JwtUtil;
import com.ttnails.booking_service.users.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S0-02: khoá hợp đồng error response
 * {code, errorCode, message, details?, traceId, timestamp} + mapping HTTP status.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String tokenFor(String role) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("contract-" + role.toLowerCase() + "@ttnails.test")
                .roles(new HashSet<>(Set.of(role)))
                .build();
        return jwtUtil.generateToken(user);
    }

    @Test
    void validationFailureReturnsFieldDetails() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0].field").exists())
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void businessErrorReturnsErrorCodeAndEchoesRequestId() throws Exception {
        mockMvc.perform(get("/bookings/availability")
                        .param("date", "not-a-date")
                        .header("X-Request-Id", "trace-s0-02-001"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "trace-s0-02-001"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE"))
                .andExpect(jsonPath("$.traceId").value("trace-s0-02-001"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void notFoundUsesEnvelope() throws Exception {
        mockMvc.perform(delete("/services/{id}", 999_999L)
                        .header("Authorization", "Bearer " + tokenFor(Role.ADMIN.name())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void wrongRoleReturns403Json() throws Exception {
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + tokenFor(Role.USER.name())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unsupportedMediaTypeReturns415Json() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("email=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(415))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void openApiDocsAreAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("TT Nails Booking API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
    }
}
