package com.ttnails.booking_service.users.service;


import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import com.ttnails.booking_service.common.enums.Role;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.common.exception.NotFoundException;
import com.ttnails.booking_service.security.JwtUtil;
import com.ttnails.booking_service.users.dto.AuthRequest;
import com.ttnails.booking_service.users.dto.IntrospectRequest;
import com.ttnails.booking_service.users.dto.LogoutRequest;
import com.ttnails.booking_service.users.dto.LoginResponse;
import com.ttnails.booking_service.users.dto.PasswordChangeRequest;
import com.ttnails.booking_service.users.dto.ProfileUpdateRequest;
import com.ttnails.booking_service.users.dto.RefreshRequest;
import com.ttnails.booking_service.users.dto.RegisterRequest;
import com.ttnails.booking_service.users.dto.UserDTO;
import com.ttnails.booking_service.users.entity.InvalidToken;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.InvalidTokenRepository;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InvalidTokenRepository invalidTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    // ============================ Helpers ============================

    /** Bỏ khoảng trắng trong SĐT để so khớp (vd: "0176 12345678" -> "017612345678") */
    private String normalizePhone(String phone) {
        return phone == null ? null : phone.replace(" ", "");
    }

    private User findUserByEmailOrPhone(String email, String phone) {
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) return byEmail.get();
        }
        if (phone != null && !phone.isBlank()) {
            Optional<User> byPhone = userRepository.findByPhone(phone);
            if (byPhone.isPresent()) return byPhone.get();
            Optional<User> byPhoneNormalized = userRepository.findByPhone(normalizePhone(phone));
            if (byPhoneNormalized.isPresent()) return byPhoneNormalized.get();
        }
        throw new NotFoundException("User not found with provided email/phone");
    }

    private LoginResponse buildLoginResponse(User user) {
        return LoginResponse.builder()
                .token(jwtUtil.generateToken(user))
                .user(UserDTO.from(user))
                .build();
    }

    // ============================ Auth ============================

    /** POST /auth/login — nhận { emailOrPhone | email | phone, password }, trả { token, user } */
    public LoginResponse authenticate(AuthRequest authRequest) {
        String identifier = authRequest.getEmailOrPhone();
        String email = (authRequest.getEmail() != null && !authRequest.getEmail().isBlank())
                ? authRequest.getEmail()
                : identifier;
        String phone = (authRequest.getPhone() != null && !authRequest.getPhone().isBlank())
                ? authRequest.getPhone()
                : identifier;

        User user = findUserByEmailOrPhone(email, phone);

        boolean auth = passwordEncoder.matches(authRequest.getPassword(), user.getPassword());
        if (!auth) {
            throw new AppException(ErrorCode.FAILED_AUTHENTICATED);
        }
        return buildLoginResponse(user);
    }
    /** POST /auth/register — nhận { name, email, phone, password, birthday? }, trả { token, user } */
    public LoginResponse register(RegisterRequest request) {
        String email = request.getEmail().trim();
        String phone = normalizePhone(request.getPhone().trim());

        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_IS_ALREADY_USED);
        }
        if (userRepository.existsByPhone(phone)) {
            throw new AppException(ErrorCode.PHONE_IS_ALREADY_USED);
        }

        Set<String> roles = new HashSet<>();
        roles.add(Role.USER.name());

        User user = User.builder()
                .name(request.getName())
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(roles)
                .dob(request.getBirthday())
                .build();
        user = userRepository.save(user);
        return buildLoginResponse(user);
    }

    // ============================ Profile (/auth/me) ============================

    public UserDTO getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        return UserDTO.from(user);
    }

    public UserDTO updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim();
            if (userRepository.existsByEmailAndIdNot(email, userId)) {
                throw new AppException(ErrorCode.EMAIL_IS_ALREADY_USED);
            }
            user.setEmail(email);
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phone = normalizePhone(request.getPhone());
            if (userRepository.existsByPhoneAndIdNot(phone, userId)) {
                throw new AppException(ErrorCode.PHONE_IS_ALREADY_USED);
            }
            user.setPhone(phone);
        }
        if (request.getBirthday() != null) {
            user.setDob(request.getBirthday());
        }
        return UserDTO.from(userRepository.save(user));
    }

    /** PUT /auth/password — { currentPassword, newPassword } */
    public void changePassword(UUID userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // ============================ Token ============================

    public boolean introspect(IntrospectRequest request) throws ParseException, JOSEException {
        var verifiedToken = jwtUtil.verifyToken(request.getToken(),false);
        String id = verifiedToken.getJWTClaimsSet().getJWTID();
        if (invalidTokenRepository.existsById(id)) return false;
        return jwtUtil.validateToken(request.getToken());
    }

    public String refreshToken(RefreshRequest request) throws JOSEException, ParseException {
        SignedJWT signedJwt = jwtUtil.verifyToken(request.getToken(), true);
        String jwtId = signedJwt.getJWTClaimsSet().getJWTID();
        // Check if token logout
        if (invalidTokenRepository.existsById(jwtId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        // Mark current token as invalid
        Date expiryTime = signedJwt.getJWTClaimsSet().getExpirationTime();
        invalidTokenRepository.save(new InvalidToken(jwtId, expiryTime));
        // Generate new token
        String userEmail = signedJwt.getJWTClaimsSet().getSubject();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.FAILED_AUTHENTICATED));

        return jwtUtil.generateToken(user);
    }
    public void logout (LogoutRequest request) throws ParseException, JOSEException {
        var verifiedToken = jwtUtil.verifyToken(request.getToken(),false);
        String id = verifiedToken.getJWTClaimsSet().getJWTID();
        Date expiryTime = verifiedToken.getJWTClaimsSet().getExpirationTime();
        InvalidToken invalidToken = new InvalidToken(id,expiryTime);
        invalidTokenRepository.save(invalidToken);
    }
}
