package com.ttnails.booking_service.users.controller;

import com.nimbusds.jose.JOSEException;
import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.users.dto.AuthRequest;
import com.ttnails.booking_service.users.dto.IntrospectRequest;
import com.ttnails.booking_service.users.dto.LoginResponse;
import com.ttnails.booking_service.users.dto.LogoutRequest;
import com.ttnails.booking_service.users.dto.PasswordChangeRequest;
import com.ttnails.booking_service.users.dto.ProfileUpdateRequest;
import com.ttnails.booking_service.users.dto.RefreshRequest;
import com.ttnails.booking_service.users.dto.RegisterRequest;
import com.ttnails.booking_service.users.dto.UserDTO;
import com.ttnails.booking_service.users.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    /** POST /auth/login — body: { emailOrPhone, password } → { token, user } */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> authenticate(@Valid @RequestBody AuthRequest authRequest) {
        if (!authRequest.isValid()) {
            // Trả lỗi qua exception handler để HTTP status nhất quán (400, không phải 200 kèm code lệch)
            throw new AppException(ErrorCode.INVALID_LOGIN_REQUEST);
        }
        return new ApiResponse<>(200, "Login successfully", LocalDateTime.now(),
                authService.authenticate(authRequest));
    }

    /** POST /auth/register — body: { name, email, phone, password, birthday? } → { token, user } */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return new ApiResponse<>(201, "Register successfully", LocalDateTime.now(),
                authService.register(request));
    }

    /** GET /auth/me — thông tin user đang đăng nhập */
    @GetMapping("/me")
    public ApiResponse<UserDTO> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaim("id"));
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), authService.getCurrentUser(userId));
    }

    /** PUT /auth/me — cập nhật { name, email, phone, birthday } */
    @PutMapping("/me")
    public ApiResponse<UserDTO> updateCurrentUser(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestBody ProfileUpdateRequest request) {
        UUID userId = UUID.fromString(jwt.getClaim("id"));
        return new ApiResponse<>(200, "Profile updated", LocalDateTime.now(),
                authService.updateProfile(userId, request));
    }

    /** PUT /auth/password — body: { currentPassword, newPassword } → { success: true } */
    @PutMapping("/password")
    public ApiResponse<Map<String, Object>> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody PasswordChangeRequest request) {
        UUID userId = UUID.fromString(jwt.getClaim("id"));
        authService.changePassword(userId, request);
        return new ApiResponse<>(200, "Password updated successfully", LocalDateTime.now(),
                Map.of("success", true));
    }

    @PostMapping("/introspect")
    public ApiResponse<Boolean> introspect(@Valid @RequestBody IntrospectRequest authRequest) throws ParseException, JOSEException{
        return authService.introspect(authRequest)? new ApiResponse<>(200,"Valid Token",LocalDateTime.now(),true):new ApiResponse<>(403,"Invalid Token",LocalDateTime.now(),false);
    }

    @PostMapping("/refresh")
    public ApiResponse<String> refreshToken(@Valid @RequestBody RefreshRequest request) throws ParseException, JOSEException {
        String token = authService.refreshToken(request);
        return  token != null ? new  ApiResponse<>(200, "refresh Token successfully", LocalDateTime.now(), token):new  ApiResponse<>(403, "Login time out", LocalDateTime.now(), null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout (@RequestBody LogoutRequest authRequest) throws ParseException, JOSEException {
        authService.logout(authRequest);
        return ApiResponse.<Void>builder().build();
    }
}
