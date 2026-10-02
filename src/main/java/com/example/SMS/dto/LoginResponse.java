package com.example.SMS.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UUID userId,
        UUID tenantId,
        String email,
        Set<String> roles) {
}
