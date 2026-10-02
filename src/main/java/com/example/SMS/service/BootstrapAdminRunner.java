package com.example.SMS.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String tenantId;
    private final String email;
    private final String password;
    private final String displayName;

    public BootstrapAdminRunner(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${sms.bootstrap.tenant-id:}") String tenantId,
            @Value("${sms.bootstrap.email:}") String email,
            @Value("${sms.bootstrap.password:}") String password,
            @Value("${sms.bootstrap.display-name:}") String displayName) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tenantId = tenantId;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean configured = !tenantId.isBlank() || !email.isBlank() || !password.isBlank()
                || !displayName.isBlank();
        if (!configured) {
            return;
        }
        if (tenantId.isBlank() || email.isBlank() || password.isBlank() || displayName.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap provisioning requires SMS_BOOTSTRAP_TENANT_ID, EMAIL, PASSWORD, and DISPLAY_NAME together.");
        }
        if (password.length() < 12 || email.length() > 254 || displayName.length() > 180) {
            throw new IllegalStateException("Bootstrap password must have at least 12 characters; email/name must fit schema.");
        }
        UUID parsedTenantId = UUID.fromString(tenantId);
        Boolean activeTenant = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM tenants WHERE id = ? AND status = 'ACTIVE')",
                Boolean.class, parsedTenantId);
        if (!Boolean.TRUE.equals(activeTenant)) {
            throw new IllegalStateException("Bootstrap tenant does not exist or is not active.");
        }
        Integer duplicate = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_users WHERE tenant_id = ? AND lower(email) = lower(?)",
                Integer.class, parsedTenantId, email.trim());
        if (duplicate != null && duplicate > 0) {
            throw new IllegalStateException("Bootstrap user already exists; refusing to grant or reset privileges.");
        }
        UUID userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_users (tenant_id, email, password_hash, display_name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE') RETURNING id
                """, UUID.class, parsedTenantId, email.trim().toLowerCase(), passwordEncoder.encode(password),
                displayName.trim());
        jdbcTemplate.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, id FROM roles WHERE code = 'SCHOOL_ADMIN'
                """, userId);
    }
}
