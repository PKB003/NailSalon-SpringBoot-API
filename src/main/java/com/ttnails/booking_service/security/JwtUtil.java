package com.ttnails.booking_service.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ttnails.booking_service.entity.User;
import com.ttnails.booking_service.exception.AppException;
import com.ttnails.booking_service.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.UUID;

@Component
@AllArgsConstructor
public class JwtUtil {
    @Value("${JWT_SECRET_KEY}")
    @NonFinal
    protected static String secret;
    @Value("${JWT_VALID_DURATION}")
    @NonFinal
    protected static long valid_duration;
    @Value("${JWT_REFRESH_DURATION}")
    @NonFinal
    protected static long refresh_duration;
    public String generateToken(User user) {
        try {
            JWSSigner signer = new MACSigner(secret);

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(user.getEmail())
                    .issuer("ttnails.com")
                    .issueTime(new Date())
                    .expirationTime(new Date(Instant.now().plus(valid_duration, ChronoUnit.SECONDS).toEpochMilli()))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("scope",buildScope(user))
                    .build();
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claimsSet);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new AppException(ErrorCode.ERROR_IN_GENERATING_TOKEN);
        }
    }
    public boolean validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret);
            return signedJWT.verify(verifier) &&
                    new Date().before(signedJWT.getJWTClaimsSet().getExpirationTime());
        } catch (Exception e) {
            return false;
        }
    }

    public SignedJWT verifyToken(String token, boolean isRefresh) throws ParseException, JOSEException {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret);
            Date expiredTime = isRefresh?
                    new Date(signedJWT.getJWTClaimsSet().getIssueTime().toInstant().plus(refresh_duration,ChronoUnit.SECONDS).toEpochMilli())
                    :signedJWT.getJWTClaimsSet().getExpirationTime();
            if(!(signedJWT.verify(verifier) && new Date().before(expiredTime))){
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
            return signedJWT;
    }

    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");
        if(!CollectionUtils.isEmpty(user.getRoles())) {
            user.getRoles().forEach(stringJoiner::add);
        }
        return stringJoiner.toString();
    }
}
