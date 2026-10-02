package com.example.SMS.service;

import com.example.SMS.dto.LoginRequest;
import com.example.SMS.dto.LoginResponse;
import com.example.SMS.dto.RegistrationRequest;
import com.example.SMS.security.AuthenticatedUser;
import com.example.SMS.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long tokenMinutes;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${sms.auth.access-token-minutes:15}") long tokenMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenMinutes = tokenMinutes;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        var accounts = jdbcTemplate.query("""
                    SELECT u.id, u.tenant_id, u.email, u.password_hash
                    FROM app_users u
                    JOIN tenants t ON t.id = u.tenant_id
                    WHERE t.slug = ? AND lower(u.email) = lower(?)
                      AND u.status = 'ACTIVE' AND t.status = 'ACTIVE'
                    """, (rs, rowNum) -> new LoginAccount(
                    rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getString("email"),
                    rs.getString("password_hash")), request.tenantSlug().trim(), request.email().trim());
        if (accounts.size() != 1) {
            throw invalidCredentials();
        }
        LoginAccount account = accounts.getFirst();
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw invalidCredentials();
        }
        Set<String> roles = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT r.code FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                """, String.class, account.id()));
        if (roles.isEmpty()) {
            throw invalidCredentials();
        }
        jdbcTemplate.update("UPDATE app_users SET last_login_at = now() WHERE id = ?", account.id());
        AuthenticatedUser user = new AuthenticatedUser(account.id(), account.tenantId(), account.email(), roles);
        return new LoginResponse(jwtService.issue(user), "Bearer",
                Instant.now().plusSeconds(tokenMinutes * 60), user.id(), user.tenantId(), user.email(), roles);
    }

    @Transactional
    public LoginResponse register(RegistrationRequest request) {
        UUID tenantId = jdbcTemplate.queryForObject("""
                INSERT INTO tenants (name, slug, status)
                VALUES (?, ?, 'ACTIVE')
                RETURNING id
                """, UUID.class, request.tenantName().trim(), request.tenantSlug().trim());

        UUID userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_users (tenant_id, email, password_hash, display_name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                RETURNING id
                """, UUID.class, tenantId, request.email().trim().toLowerCase(),
                passwordEncoder.encode(request.password()), request.displayName().trim());

        int roleAssigned = jdbcTemplate.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, id FROM roles WHERE code = 'SCHOOL_ADMIN'
                """, userId);
        if (roleAssigned != 1) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR,
                    "School administrator role is not configured.");
        }

        Set<String> roles = Set.of("SCHOOL_ADMIN");
        AuthenticatedUser user = new AuthenticatedUser(userId, tenantId,
                request.email().trim().toLowerCase(), roles);
        return new LoginResponse(jwtService.issue(user), "Bearer",
                Instant.now().plusSeconds(tokenMinutes * 60), userId, tenantId, user.email(), roles);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(UNAUTHORIZED, "Invalid tenant, email, or password");
    }

    private record LoginAccount(UUID id, UUID tenantId, String email, String passwordHash) {
    }
}
