package com.ttnails.booking_service.service;


import com.nimbusds.jose.JOSEException;
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
        User user = userRepository.findByPhoneOrEmail(authRequest.getPhone(),authRequest.getEmail()).orElseThrow(() -> new NotFoundException("Can't not find user"));
        boolean auth = passwordEncoder.matches(authRequest.getPassword(),user.getPassword());
        if(!auth) throw new AppException(ErrorCode.FAILED_AUTHENTICATED);
        return jwtUtil.generateToken(user);
    }
    public boolean introspect(IntrospectRequest request) throws ParseException, JOSEException {
        var verifiedToken = jwtUtil.verifyToken(request.getToken(),false);
        String id = verifiedToken.getJWTClaimsSet().getJWTID();
        if (invalidTokenRepository.existsById(id)) return false;
        return jwtUtil.validateToken(request.getToken());
    }

    public String refreshToken(RefreshRequest request) throws JOSEException, ParseException {
        var signedJwt = jwtUtil.verifyToken(request.getToken(),true);
        var jwtId = signedJwt.getJWTClaimsSet().getJWTID();
        Date expiryTime = signedJwt.getJWTClaimsSet().getExpirationTime();
        InvalidToken invalidToken = new InvalidToken(jwtId,expiryTime);
        invalidTokenRepository.save(invalidToken);
        var userName = signedJwt.getJWTClaimsSet().getSubject();
        var user = userRepository.findByEmail(userName).orElseThrow(() -> new AppException(ErrorCode.FAILED_AUTHENTICATED));
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
