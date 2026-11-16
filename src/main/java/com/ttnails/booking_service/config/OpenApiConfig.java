package com.ttnails.booking_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger UI (S0-01).
 *
 * <p>Nguồn sự thật cho client TypeScript là {@code /v3/api-docs} (sinh tự động từ code).
 * File {@code openapi.yaml} ở root là bản contract đóng băng v1 để review và generate SDK.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${app.openapi.server-url:http://localhost:8080}")
    private String serverUrl;

    @Bean
    public OpenAPI bookingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TT Nails Booking API")
                        .version("1.0.0")
                        .description("""
                                Backend API cho tiệm nail tại Đức (EUR, hoá đơn tiếng Đức).

                                * Mọi response thành công dùng envelope `{code, message, timestamp, data, traceId}`.
                                * Mọi response lỗi dùng `{code, errorCode, message, details, traceId, timestamp}`;
                                  `errorCode` là mã máy đọc (ví dụ `SLOT_FULL`, `VALIDATION_FAILED`).
                                * Header `X-Request-Id` được trả lại trên mọi request và khớp với `traceId`.
                                * Endpoint `/booking/**` là legacy, đã deprecated — dùng `/bookings/**`.
                                """)
                        .contact(new Contact().name("TT Nails").email("dev@ttnails.local")))
                .servers(List.of(new Server().url(serverUrl).description("Local / staging")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT lấy từ POST /auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
