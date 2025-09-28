package com.ttnails.booking_service.controller;

import com.nimbusds.jose.JOSEException;
import com.ttnails.booking_service.dto.*;
import com.ttnails.booking_service.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthService authService;
    @PostMapping("/login")
    public ApiResponse<String> authenticate(@Valid @RequestBody AuthRequest authRequest) {
        if (!authRequest.isValid()) {
            return new ApiResponse<>(400,"You must enter at least email or phone", LocalDateTime.now(), null);
        }
        String token = authService.authenticate(authRequest);
        return  token != null ? new  ApiResponse<>(200, "Login successfully", LocalDateTime.now(), token):new  ApiResponse<>(403, "Login failed", LocalDateTime.now(), null);
    }
    @PostMapping("/introspect")
    public ApiResponse<Boolean> introspect(@Valid @RequestBody IntrospectRequest authRequest) throws ParseException, JOSEException{
        return authService.introspect(authRequest)? new ApiResponse<>(200,"Valid Token",LocalDateTime.now(),true):new ApiResponse<>(403,"Invalid Token",LocalDateTime.now(),false);
    }

    @PostMapping("/refresh")
    public ApiResponse<String> authenticate(@Valid @RequestBody RefreshRequest request) throws ParseException, JOSEException {
        String token = authService.refreshToken(request);
        return  token != null ? new  ApiResponse<>(200, "refresh Token successfully", LocalDateTime.now(), token):new  ApiResponse<>(403, "Login time out", LocalDateTime.now(), null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout (@RequestBody LogoutRequest authRequest) throws ParseException, JOSEException {
        authService.logout(authRequest);
        return ApiResponse.<Void>builder().build();
    }
}
