package com.example.SMS.service;

import com.example.SMS.dto.StaffRequests;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class StaffService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public StaffService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public List<StaffView> list(UUID tenantId) {
        return jdbc.query("""
                SELECT u.id, u.email, u.display_name, u.status, u.created_at, u.updated_at
                FROM app_users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id AND r.code = 'TEACHER'
                WHERE u.tenant_id = ?
                ORDER BY u.display_name, u.email
                """, (rs, row) -> new StaffView(
                rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)), tenantId);
    }

    public StaffView get(UUID tenantId, UUID userId) {
        List<StaffView> rows = jdbc.query("""
                SELECT u.id, u.email, u.display_name, u.status, u.created_at, u.updated_at
                FROM app_users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id AND r.code = 'TEACHER'
                WHERE u.tenant_id = ? AND u.id = ?
                """, (rs, row) -> new StaffView(
                rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)), tenantId, userId);
        return found(rows);
    }

    @Transactional
    public StaffView create(UUID tenantId, StaffRequests.Create request) {
        assertEmailAvailable(tenantId, request.email(), null);
        UUID userId = jdbc.queryForObject("""
                INSERT INTO app_users (tenant_id, email, password_hash, display_name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE') RETURNING id
                """, UUID.class, tenantId, request.email().trim().toLowerCase(),
                passwordEncoder.encode(request.password()), request.displayName().trim());
        jdbc.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, id FROM roles WHERE code = 'TEACHER'
                """, userId);
        return get(tenantId, userId);
    }

    @Transactional
    public StaffView update(UUID tenantId, UUID userId, StaffRequests.Update request) {
        assertEmailAvailable(tenantId, request.email(), userId);
        List<StaffView> rows = jdbc.query("""
                UPDATE app_users u SET email = ?, display_name = ?, status = ?, updated_at = now()
                WHERE u.tenant_id = ? AND u.id = ?
                  AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                              WHERE ur.user_id = u.id AND r.code = 'TEACHER')
                RETURNING u.id, u.email, u.display_name, u.status, u.created_at, u.updated_at
                """, (rs, row) -> new StaffView(
                rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)),
                request.email().trim().toLowerCase(), request.displayName().trim(), request.status(),
                tenantId, userId);
        return found(rows);
    }

    @Transactional
    public StaffView setPassword(UUID tenantId, UUID userId, String password) {
        if (jdbc.update("""
                UPDATE app_users SET password_hash = ?, updated_at = now()
                WHERE tenant_id = ? AND id = ?
                  AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                              WHERE ur.user_id = app_users.id AND r.code = 'TEACHER')
                """, passwordEncoder.encode(password), tenantId, userId) == 0) {
            throw missing();
        }
        return get(tenantId, userId);
    }

    @Transactional
    public void delete(UUID tenantId, UUID userId, UUID actorId) {
        if (userId.equals(actorId)) {
            throw new ResponseStatusException(CONFLICT, "You cannot delete your own account");
        }
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM app_users u
                    JOIN user_roles ur ON ur.user_id = u.id
                    JOIN roles r ON r.id = ur.role_id AND r.code = 'TEACHER'
                    WHERE u.tenant_id = ? AND u.id = ?
                )
                """, Boolean.class, tenantId, userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw missing();
        }
        Boolean referenced = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM students WHERE user_id = ?)
                    OR EXISTS (SELECT 1 FROM assignments WHERE teacher_user_id = ?)
                    OR EXISTS (SELECT 1 FROM timetable_slots WHERE teacher_user_id = ?)
                    OR EXISTS (SELECT 1 FROM leave_approvals WHERE approver_user_id = ?)
                    OR EXISTS (SELECT 1 FROM announcements WHERE author_user_id = ?)
                    OR EXISTS (SELECT 1 FROM messages WHERE sender_user_id = ?)
                """, Boolean.class, userId, userId, userId, userId, userId, userId);
        if (Boolean.TRUE.equals(referenced)) {
            throw new ResponseStatusException(CONFLICT,
                    "Teacher is referenced by existing records. Disable the account instead of deleting it.");
        }
        int deleted = jdbc.update("""
                DELETE FROM app_users u
                WHERE u.tenant_id = ? AND u.id = ?
                  AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                              WHERE ur.user_id = u.id AND r.code = 'TEACHER')
                """, tenantId, userId);
        if (deleted == 0) {
            throw missing();
        }
    }

    private StaffView found(List<StaffView> rows) {
        if (rows.isEmpty()) {
            throw missing();
        }
        return rows.getFirst();
    }

    private void assertEmailAvailable(UUID tenantId, String email, UUID excludedUserId) {
        Boolean duplicate = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM app_users
                    WHERE tenant_id = ? AND lower(email) = lower(?)
                      AND (CAST(? AS UUID) IS NULL OR id <> ?)
                )
                """, Boolean.class, tenantId, email.trim(), excludedUserId, excludedUserId);
        if (Boolean.TRUE.equals(duplicate)) {
            throw new ResponseStatusException(CONFLICT, "A user with that email already exists in this tenant");
        }
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(NOT_FOUND, "Teacher not found");
    }

    public record StaffView(
            UUID id, String email, String displayName, String status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
