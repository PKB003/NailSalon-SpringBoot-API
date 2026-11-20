package com.ttnails.booking_service.tse;

import com.ttnails.booking_service.common.enums.Role;
import com.ttnails.booking_service.support.TestSupport;
import com.ttnails.booking_service.tse.client.TseClient;
import com.ttnails.booking_service.tse.client.TsePaymentType;
import com.ttnails.booking_service.tse.client.TseProviderStatus;
import com.ttnails.booking_service.tse.client.TseReceipt;
import com.ttnails.booking_service.tse.client.TseReceiptType;
import com.ttnails.booking_service.tse.client.TseSignature;
import com.ttnails.booking_service.tse.client.TseTransactionStatus;
import com.ttnails.booking_service.tse.client.TseVatAmount;
import com.ttnails.booking_service.tse.config.TseProperties;
import com.ttnails.booking_service.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 3 — TSE/fiskaly groundwork khi <b>chưa có key</b>.
 *
 * <p>Đảm bảo: (1) mặc định TSE tắt và checkout không phụ thuộc TSE;
 * (2) {@code GET /tse/status} chỉ cho MANAGER/ADMIN và không lộ secret;
 * (3) no-op client trả SKIPPED thay vì ném lỗi.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class TseStatusTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    TseClient tseClient;
    @Autowired
    TseProperties tseProperties;

    String adminToken;
    String managerToken;
    String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestSupport.adminToken(mockMvc, mapper);
        managerToken = TestSupport.userTokenWithRole(mockMvc, mapper, userRepository, passwordEncoder,
                "s3.tse.manager@test.de", Role.MANAGER);
        customerToken = TestSupport.userTokenWithRole(mockMvc, mapper, userRepository, passwordEncoder,
                "s3.tse.customer@test.de", Role.USER);
    }

    @Test
    void anonymousCannotReadTseStatus() throws Exception {
        mockMvc.perform(get("/tse/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotReadTseStatus() throws Exception {
        mockMvc.perform(get("/tse/status")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerSeesDisabledNoopStatusWithoutSecrets() throws Exception {
        mockMvc.perform(get("/tse/status")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.provider").value("fiskaly"))
                .andExpect(jsonPath("$.data.environment").value("SANDBOX"))
                .andExpect(jsonPath("$.data.implementation").value("NOOP"))
                .andExpect(jsonPath("$.data.credentialsPresent").value(false))
                .andExpect(jsonPath("$.data.tssId").value(""))
                .andExpect(jsonPath("$.data.clientId").value(""))
                .andExpect(jsonPath("$.data.timeoutMs").value(3000))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.apiSecret").doesNotExist());
    }

    @Test
    void adminSeesSameStatus() throws Exception {
        mockMvc.perform(get("/tse/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.implementation").value("NOOP"));
    }

    @Test
    void noopClientSkipsSigningInsteadOfFailing() {
        UUID txId = UUID.randomUUID();
        TseReceipt receipt = new TseReceipt(
                TseReceiptType.RECEIPT,
                "EUR",
                Map.of(0, new TseVatAmount(new BigDecimal("80.00"), new BigDecimal("80.00"), BigDecimal.ZERO)),
                Map.of(TsePaymentType.CASH, new BigDecimal("80.00")));

        tseClient.start(txId, receipt);
        TseSignature signature = tseClient.finish(txId);

        assertThat(signature.status()).isEqualTo(TseTransactionStatus.SKIPPED);
        assertThat(signature.signed()).isFalse();
        assertThat(signature.txId()).isEqualTo(txId);
        assertThat(signature.signatureValue()).isNull();

        TseProviderStatus status = tseClient.status();
        assertThat(status.enabled()).isFalse();
        assertThat(status.implementation()).isEqualTo("NOOP");
    }

    @Test
    void propertiesMaskIdsAndDetectMissingCredentials() {
        assertThat(TseProperties.mask(null)).isEmpty();
        assertThat(TseProperties.mask("abc")).isEqualTo("***");
        assertThat(TseProperties.mask("tss_1234567890abcdef")).isEqualTo("tss_***ef");

        assertThat(tseProperties.hasCredentials()).isFalse();
        assertThat(tseProperties.isEnabled()).isFalse();
        assertThat(tseProperties.getRetry().getMaxAttempts()).isEqualTo(3);
    }
}
