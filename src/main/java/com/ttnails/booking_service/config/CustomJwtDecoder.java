package com.ttnails.booking_service.config;

import com.nimbusds.jwt.SignedJWT;
import com.ttnails.booking_service.repository.InvalidTokenRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
@Component
@RequiredArgsConstructor
public class CustomJwtDecoder implements JwtDecoder {

    private final InvalidTokenRepository invalidTokenRepository;

    @Value("${JWT_SECRET_KEY}")
    private String secretKey;

    private NimbusJwtDecoder nimbusJwtDecoder;

    @PostConstruct
    public void init() {
        SecretKeySpec spec = new SecretKeySpec(secretKey.getBytes(), "HS512");
        nimbusJwtDecoder = NimbusJwtDecoder
                .withSecretKey(spec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String jwtId = signedJWT.getJWTClaimsSet().getJWTID();

            if (invalidTokenRepository.existsById(jwtId)) {
                throw new JwtException("Token has been invalidated (logout).");
            }

            return nimbusJwtDecoder.decode(token);

        } catch (Exception e) {
            throw new JwtException("Invalid token", e);
        }
    }
}
