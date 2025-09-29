package com.ttnails.booking_service.service;


import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import com.ttnails.booking_service.dto.AuthRequest;
import com.ttnails.booking_service.dto.IntrospectRequest;
import com.ttnails.booking_service.dto.LogoutRequest;
import com.ttnails.booking_service.dto.RefreshRequest;
import com.ttnails.booking_service.entity.InvalidToken;
import com.ttnails.booking_service.entity.User;
import com.ttnails.booking_service.exception.AppException;
import com.ttnails.booking_service.exception.ErrorCode;
import com.ttnails.booking_service.exception.NotFoundException;
import com.ttnails.booking_service.repository.InvalidTokenRepository;
import com.ttnails.booking_service.repository.UserRepository;
import com.ttnails.booking_service.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InvalidTokenRepository invalidTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;
    public String authenticate(AuthRequest authRequest) {
        User user;
        if (authRequest.getEmail() != null && !authRequest.getEmail().isEmpty()) {
            user = userRepository.findByEmail(authRequest.getEmail())
                    .orElseThrow(() -> new NotFoundException("User not found with email"));
        } else if (authRequest.getPhone() != null && !authRequest.getPhone().isEmpty()) {
            user = userRepository.findByPhone(authRequest.getPhone())
                    .orElseThrow(() -> new NotFoundException("User not found with phone"));
        } else {
            throw new NotFoundException("You must provide email or phone");
        }

        boolean auth = passwordEncoder.matches(authRequest.getPassword(), user.getPassword());
        if (!auth) {
            throw new AppException(ErrorCode.FAILED_AUTHENTICATED);
        }
        return jwtUtil.generateToken(user);
    }
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
