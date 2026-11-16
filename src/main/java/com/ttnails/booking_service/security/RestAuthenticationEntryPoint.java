package com.ttnails.booking_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Trả 401 dạng JSON thống nhất thay vì body rỗng (S0-02).
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTHENTICATION_REQUIRED.name(),
                ErrorCode.AUTHENTICATION_REQUIRED.getMessage(),
                null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
