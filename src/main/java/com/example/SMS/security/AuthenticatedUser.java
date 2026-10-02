package com.example.SMS.security;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(UUID id, UUID tenantId, String email, Set<String> roles) {
}
