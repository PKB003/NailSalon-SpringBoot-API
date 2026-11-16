package com.ttnails.booking_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Trả 403 dạng JSON thống nhất thay vì body rỗng (S0-02).
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED.name(),
                ErrorCode.ACCESS_DENIED.getMessage(),
                null);
        objectMapper.writeValue(response.getWriter(), body);
        MDC.remove("requestId");
    }
}
