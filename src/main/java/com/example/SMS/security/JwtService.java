package com.example.SMS.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtService {
    private static final String ISSUER = "sms-api";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final byte[] secret;
    private final long tokenSeconds;
    private final ObjectMapper objectMapper;

    public JwtService(
            @Value("${sms.auth.jwt-secret:}") String configuredSecret,
            @Value("${sms.auth.access-token-minutes:15}") long tokenMinutes,
            ObjectMapper objectMapper) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                    "Authentication requires SMS_JWT_SECRET: configure a random secret of at least 32 bytes.");
        }
        byte[] key;
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredSecret);
            key = decoded.length >= 32 ? decoded : configuredSecret.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            key = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (key.length < 32) {
            throw new IllegalStateException("SMS_JWT_SECRET must contain at least 32 bytes (256 bits).");
        }
        if (tokenMinutes < 1 || tokenMinutes > 60) {
            throw new IllegalStateException("sms.auth.access-token-minutes must be between 1 and 60.");
        }
        this.secret = key;
        this.tokenSeconds = tokenMinutes * 60;
        this.objectMapper = objectMapper;
    }

    public String issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(objectMapper.writeValueAsBytes(Map.of(
                    "iss", ISSUER,
                    "sub", user.id().toString(),
                    "tenant", user.tenantId().toString(),
                    "email", user.email(),
                    "roles", user.roles(),
                    "iat", now.getEpochSecond(),
                    "exp", now.plusSeconds(tokenSeconds).getEpochSecond())));
            String signingInput = header + "." + payload;
            return signingInput + "." + ENCODER.encodeToString(sign(signingInput));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue access token.", exception);
        }
    }

    public AuthenticatedUser verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Malformed token");
            }
            Map<String, Object> header = objectMapper.readValue(DECODER.decode(parts[0]), new TypeReference<>() {});
            if (!"HS256".equals(header.get("alg"))) {
                throw new IllegalArgumentException("Unsupported token algorithm");
            }
            byte[] suppliedSignature = DECODER.decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), suppliedSignature)) {
                throw new IllegalArgumentException("Invalid token signature");
            }
            Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), new TypeReference<>() {});
            long expiresAt = ((Number) claims.get("exp")).longValue();
            long issuedAt = ((Number) claims.get("iat")).longValue();
            Instant now = Instant.now();
            if (!ISSUER.equals(claims.get("iss"))
                    || expiresAt <= now.getEpochSecond()
                    || issuedAt > now.plusSeconds(30).getEpochSecond()
                    || expiresAt - issuedAt > tokenSeconds) {
                throw new IllegalArgumentException("Expired or invalid token claims");
            }
            Object rawRoles = claims.get("roles");
            if (!(rawRoles instanceof java.util.List<?> roleList)) {
                throw new IllegalArgumentException("Invalid token roles");
            }
            Set<String> roles = roleList.stream().map(value -> {
                if (!(value instanceof String role) || !role.matches("[A-Z_]{2,40}")) {
                    throw new IllegalArgumentException("Invalid token role");
                }
                return role;
            }).collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("Token has no roles");
            }
            return new AuthenticatedUser(
                    UUID.fromString((String) claims.get("sub")),
                    UUID.fromString((String) claims.get("tenant")),
                    (String) claims.get("email"),
                    roles);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid access token.", exception);
        }
    }

    private String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private byte[] sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(content.getBytes(StandardCharsets.US_ASCII));
    }
}
