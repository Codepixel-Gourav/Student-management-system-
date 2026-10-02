package com.example.SMS.service;

import com.example.SMS.dto.AttendanceRequests;
import com.example.SMS.security.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class AttendanceService {
    private final JdbcTemplate jdbc;

    public AttendanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SessionView> listSessions(AuthenticatedUser user) {
        boolean admin = hasAdminRole(user);
        return jdbc.query("""
                SELECT s.id, s.class_section_id, s.course_id, s.teacher_user_id, s.starts_at, s.ends_at
                FROM attendance_sessions s
                JOIN class_sections cs ON cs.id = s.class_section_id
                WHERE cs.tenant_id = ? AND (? = TRUE OR s.teacher_user_id = ?)
                ORDER BY s.starts_at DESC LIMIT 500
                """, (rs, row) -> session(rs.getObject("id", UUID.class),
                rs.getObject("class_section_id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("teacher_user_id", UUID.class), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class)), user.tenantId(), admin, user.id());
    }

    public SessionView getSession(AuthenticatedUser user, UUID id) {
        SessionView session = getSession(user.tenantId(), id);
        assertSessionAccess(user, session.id());
        return session;
    }

    public SessionView getSession(UUID tenantId, UUID id) {
        List<SessionView> rows = jdbc.query("""
                SELECT s.id, s.class_section_id, s.course_id, s.teacher_user_id, s.starts_at, s.ends_at
                FROM attendance_sessions s JOIN class_sections cs ON cs.id = s.class_section_id
                WHERE cs.tenant_id = ? AND s.id = ?
                """, (rs, row) -> session(rs.getObject("id", UUID.class),
                rs.getObject("class_section_id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("teacher_user_id", UUID.class), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class)), tenantId, id);
        return found(rows, "Attendance session");
    }

    @Transactional
    public SessionView createSession(UUID tenantId, AttendanceRequests.Session request) {
        validateSession(request);
        List<SessionView> rows = jdbc.query("""
                INSERT INTO attendance_sessions (class_section_id, course_id, teacher_user_id, starts_at, ends_at)
                SELECT cs.id, ?, ?, ?, ? FROM class_sections cs
                WHERE cs.id = ? AND cs.tenant_id = ?
                  AND (CAST(? AS UUID) IS NULL OR EXISTS
                       (SELECT 1 FROM courses c WHERE c.id = ? AND c.tenant_id = ?))
                  AND (CAST(? AS UUID) IS NULL OR EXISTS
                       (SELECT 1 FROM app_users u JOIN user_roles ur ON ur.user_id = u.id
                        JOIN roles r ON r.id = ur.role_id AND r.code = 'TEACHER'
                        WHERE u.id = ? AND u.tenant_id = ? AND u.status = 'ACTIVE'))
                RETURNING id, class_section_id, course_id, teacher_user_id, starts_at, ends_at
                """, (rs, row) -> session(rs.getObject("id", UUID.class),
                rs.getObject("class_section_id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("teacher_user_id", UUID.class), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class)),
                request.courseId(), request.teacherUserId(), request.startsAt(), request.endsAt(),
                request.classSectionId(), tenantId,
                request.courseId(), request.courseId(), tenantId,
                request.teacherUserId(), request.teacherUserId(), tenantId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Section, course, and teacher must exist in your tenant; teacher must be active");
        }
        return rows.getFirst();
    }

    @Transactional
    public SessionView updateSession(UUID tenantId, UUID id, AttendanceRequests.Session request) {
        validateSession(request);
        UUID currentSectionId = lockSessionSection(tenantId, id);
        if (!currentSectionId.equals(request.classSectionId())) {
            Boolean wouldMoveMarkedSession = jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM attendance_sessions s
                        JOIN class_sections cs ON cs.id = s.class_section_id
                        JOIN attendance_records ar ON ar.session_id = s.id
                        WHERE cs.tenant_id = ? AND s.id = ? AND s.class_section_id <> ?
                    )
                    """, Boolean.class, tenantId, id, request.classSectionId());
            if (Boolean.TRUE.equals(wouldMoveMarkedSession)) {
                throw new ResponseStatusException(CONFLICT,
                        "A session with attendance records cannot be moved to a different class section");
            }
        }
        List<SessionView> rows = jdbc.query("""
                UPDATE attendance_sessions s
                SET class_section_id = ?, course_id = ?, teacher_user_id = ?, starts_at = ?, ends_at = ?
                WHERE s.id = ? AND EXISTS
                      (SELECT 1 FROM class_sections own WHERE own.id = s.class_section_id AND own.tenant_id = ?)
                  AND EXISTS (SELECT 1 FROM class_sections target
                              WHERE target.id = ? AND target.tenant_id = ?)
                  AND (CAST(? AS UUID) IS NULL OR EXISTS
                       (SELECT 1 FROM courses c WHERE c.id = ? AND c.tenant_id = ?))
                  AND (CAST(? AS UUID) IS NULL OR EXISTS
                       (SELECT 1 FROM app_users u JOIN user_roles ur ON ur.user_id = u.id
                        JOIN roles r ON r.id = ur.role_id AND r.code = 'TEACHER'
                        WHERE u.id = ? AND u.tenant_id = ? AND u.status = 'ACTIVE'))
                RETURNING s.id, s.class_section_id, s.course_id, s.teacher_user_id, s.starts_at, s.ends_at
                """, (rs, row) -> session(rs.getObject("id", UUID.class),
                rs.getObject("class_section_id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("teacher_user_id", UUID.class), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class)),
                request.classSectionId(), request.courseId(), request.teacherUserId(),
                request.startsAt(), request.endsAt(), id, tenantId,
                request.classSectionId(), tenantId, request.courseId(), request.courseId(), tenantId,
                request.teacherUserId(), request.teacherUserId(), tenantId);
        if (rows.isEmpty()) {
            throw missing("Attendance session");
        }
        return rows.getFirst();
    }

    @Transactional
    public void deleteSession(UUID tenantId, UUID id) {
        int deleted = jdbc.update("""
                DELETE FROM attendance_sessions s WHERE s.id = ?
                  AND EXISTS (SELECT 1 FROM class_sections cs WHERE cs.id = s.class_section_id AND cs.tenant_id = ?)
                """, id, tenantId);
        if (deleted == 0) {
            throw missing("Attendance session");
        }
    }

    public List<RecordView> records(AuthenticatedUser user, UUID sessionId) {
        assertSessionAccess(user, sessionId);
        return jdbc.query("""
                SELECT ar.id, ar.session_id, ar.student_id, ar.status, ar.source, ar.marked_at
                FROM attendance_records ar
                JOIN attendance_sessions s ON s.id = ar.session_id
                JOIN class_sections cs ON cs.id = s.class_section_id
                WHERE cs.tenant_id = ? AND s.id = ? ORDER BY ar.marked_at
                """, (rs, row) -> record(rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("status"), rs.getString("source"),
                rs.getObject("marked_at", OffsetDateTime.class)), user.tenantId(), sessionId);
    }

    public List<AttendanceStudentView> eligibleStudents(AuthenticatedUser user, UUID sessionId) {
        assertSessionAccess(user, sessionId);
        return jdbc.query("""
                SELECT st.id, st.enrollment_no, st.first_name, st.last_name
                FROM attendance_sessions s
                JOIN class_sections cs ON cs.id = s.class_section_id
                JOIN enrollments e ON e.class_section_id = cs.id AND e.status = 'ACTIVE'
                JOIN students st ON st.id = e.student_id AND st.tenant_id = cs.tenant_id
                WHERE cs.tenant_id = ? AND s.id = ?
                ORDER BY st.last_name, st.first_name, st.enrollment_no
                """, (rs, row) -> new AttendanceStudentView(
                rs.getObject("id", UUID.class), rs.getString("enrollment_no"),
                rs.getString("first_name"), rs.getString("last_name")), user.tenantId(), sessionId);
    }

    public RecordView getRecord(AuthenticatedUser user, UUID id) {
        RecordView record = found(jdbc.query("""
                SELECT ar.id, ar.session_id, ar.student_id, ar.status, ar.source, ar.marked_at
                FROM attendance_records ar
                JOIN attendance_sessions s ON s.id = ar.session_id
                JOIN class_sections cs ON cs.id = s.class_section_id
                WHERE cs.tenant_id = ? AND ar.id = ?
                """, (rs, row) -> record(rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("status"), rs.getString("source"),
                rs.getObject("marked_at", OffsetDateTime.class)), user.tenantId(), id), "Attendance record");
        assertSessionAccess(user, record.sessionId());
        return record;
    }

    @Transactional
    public RecordView createRecord(AuthenticatedUser user, UUID sessionId, AttendanceRequests.Record request) {
        UUID tenantId = user.tenantId();
        lockSessionSection(tenantId, sessionId);
        assertSessionAccess(user, sessionId);
        List<RecordView> rows = jdbc.query("""
                INSERT INTO attendance_records (session_id, student_id, status, source)
                SELECT s.id, st.id, ?, 'MANUAL' FROM attendance_sessions s
                JOIN class_sections cs ON cs.id = s.class_section_id
                JOIN students st ON st.id = ? AND st.tenant_id = cs.tenant_id AND st.campus_id = cs.campus_id
                JOIN enrollments e ON e.student_id = st.id AND e.class_section_id = cs.id AND e.status = 'ACTIVE'
                WHERE s.id = ? AND cs.tenant_id = ?
                RETURNING id, session_id, student_id, status, source, marked_at
                """, (rs, row) -> record(rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("status"), rs.getString("source"),
                rs.getObject("marked_at", OffsetDateTime.class)),
                request.status(), request.studentId(), sessionId, tenantId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Session and actively enrolled student must belong to your tenant and campus");
        }
        return rows.getFirst();
    }

    @Transactional
    public RecordView updateRecord(AuthenticatedUser user, UUID id, String status) {
        UUID tenantId = user.tenantId();
        RecordView current = getRecord(user, id);
        assertSessionAccess(user, current.sessionId());
        List<RecordView> rows = jdbc.query("""
                UPDATE attendance_records ar SET status = ?, source = 'MANUAL', marked_at = now()
                WHERE ar.id = ? AND EXISTS
                  (SELECT 1 FROM attendance_sessions s JOIN class_sections cs ON cs.id = s.class_section_id
                   WHERE s.id = ar.session_id AND cs.tenant_id = ?)
                RETURNING ar.id, ar.session_id, ar.student_id, ar.status, ar.source, ar.marked_at
                """, (rs, row) -> record(rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("status"), rs.getString("source"),
                rs.getObject("marked_at", OffsetDateTime.class)), status, id, tenantId);
        return found(rows, "Attendance record");
    }

    @Transactional
    public void deleteRecord(UUID tenantId, UUID id) {
        int deleted = jdbc.update("""
                DELETE FROM attendance_records ar WHERE ar.id = ? AND EXISTS
                  (SELECT 1 FROM attendance_sessions s JOIN class_sections cs ON cs.id = s.class_section_id
                   WHERE s.id = ar.session_id AND cs.tenant_id = ?)
                """, id, tenantId);
        if (deleted == 0) {
            throw missing("Attendance record");
        }
    }

    public List<LeaveView> leaveRequests(UUID tenantId) {
        return jdbc.query("""
                SELECT lr.id, lr.student_id, lr.starts_on, lr.ends_on, lr.reason, lr.status, lr.created_at
                FROM leave_requests lr JOIN students st ON st.id = lr.student_id
                WHERE st.tenant_id = ? ORDER BY lr.created_at DESC LIMIT 500
                """, (rs, row) -> leave(rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class), rs.getString("reason"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)), tenantId);
    }

    public LeaveView getLeaveRequest(UUID tenantId, UUID id) {
        return found(jdbc.query("""
                SELECT lr.id, lr.student_id, lr.starts_on, lr.ends_on, lr.reason, lr.status, lr.created_at
                FROM leave_requests lr JOIN students st ON st.id = lr.student_id
                WHERE st.tenant_id = ? AND lr.id = ?
                """, (rs, row) -> leave(rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class), rs.getString("reason"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)), tenantId, id), "Leave request");
    }

    @Transactional
    public LeaveView createLeaveRequest(UUID tenantId, AttendanceRequests.LeaveRequest request) {
        validateLeave(request);
        if (!"PENDING".equals(request.status())) {
            throw new ResponseStatusException(BAD_REQUEST, "New leave requests must start in PENDING status");
        }
        List<LeaveView> rows = jdbc.query("""
                INSERT INTO leave_requests (student_id, starts_on, ends_on, reason, status)
                SELECT st.id, ?, ?, ?, 'PENDING' FROM students st
                WHERE st.id = ? AND st.tenant_id = ?
                RETURNING id, student_id, starts_on, ends_on, reason, status, created_at
                """, (rs, row) -> leave(rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class), rs.getString("reason"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)),
                request.startsOn(), request.endsOn(), request.reason().trim(), request.studentId(), tenantId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Student must belong to your tenant");
        }
        return rows.getFirst();
    }

    @Transactional
    public LeaveView updateLeaveRequest(UUID tenantId, UUID id, AttendanceRequests.LeaveRequest request) {
        validateLeave(request);
        List<LeaveView> rows = jdbc.query("""
                UPDATE leave_requests lr SET student_id = ?, starts_on = ?, ends_on = ?, reason = ?, status = ?
                WHERE lr.id = ? AND EXISTS
                  (SELECT 1 FROM students old_student WHERE old_student.id = lr.student_id
                   AND old_student.tenant_id = ?)
                  AND EXISTS (SELECT 1 FROM students new_student WHERE new_student.id = ? AND new_student.tenant_id = ?)
                RETURNING lr.id, lr.student_id, lr.starts_on, lr.ends_on, lr.reason, lr.status, lr.created_at
                """, (rs, row) -> leave(rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class), rs.getString("reason"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)), request.studentId(),
                request.startsOn(), request.endsOn(), request.reason().trim(), request.status(),
                id, tenantId, request.studentId(), tenantId);
        return found(rows, "Leave request");
    }

    @Transactional
    public void deleteLeaveRequest(UUID tenantId, UUID id) {
        int deleted = jdbc.update("""
                DELETE FROM leave_requests lr WHERE lr.id = ? AND EXISTS
                  (SELECT 1 FROM students st WHERE st.id = lr.student_id AND st.tenant_id = ?)
                """, id, tenantId);
        if (deleted == 0) {
            throw missing("Leave request");
        }
    }

    private void validateSession(AttendanceRequests.Session request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ResponseStatusException(BAD_REQUEST, "Session end time must follow its start time");
        }
    }

    private void assertSessionAccess(AuthenticatedUser user, UUID sessionId) {
        if (hasAdminRole(user)) {
            getSession(user.tenantId(), sessionId);
            return;
        }
        Boolean assigned = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM attendance_sessions s
                    JOIN class_sections cs ON cs.id = s.class_section_id
                    WHERE cs.tenant_id = ? AND s.id = ? AND s.teacher_user_id = ?
                )
                """, Boolean.class, user.tenantId(), sessionId, user.id());
        if (!Boolean.TRUE.equals(assigned)) {
            throw missing("Attendance session");
        }
    }

    private boolean hasAdminRole(AuthenticatedUser user) {
        return user.roles().contains("SCHOOL_ADMIN") || user.roles().contains("SUPER_ADMIN");
    }

    private UUID lockSessionSection(UUID tenantId, UUID sessionId) {
        List<UUID> sectionIds = jdbc.query("""
                SELECT s.class_section_id FROM attendance_sessions s
                JOIN class_sections cs ON cs.id = s.class_section_id
                WHERE cs.tenant_id = ? AND s.id = ? FOR UPDATE OF s
                """, (rs, row) -> rs.getObject(1, UUID.class), tenantId, sessionId);
        return found(sectionIds, "Attendance session");
    }

    private void validateLeave(AttendanceRequests.LeaveRequest request) {
        if (request.endsOn().isBefore(request.startsOn())) {
            throw new ResponseStatusException(BAD_REQUEST, "Leave end date cannot precede its start date");
        }
    }

    private SessionView session(UUID id, UUID sectionId, UUID courseId, UUID teacherId,
                                OffsetDateTime startsAt, OffsetDateTime endsAt) {
        return new SessionView(id, sectionId, courseId, teacherId, startsAt, endsAt);
    }

    private RecordView record(UUID id, UUID sessionId, UUID studentId, String status, String source,
                              OffsetDateTime markedAt) {
        return new RecordView(id, sessionId, studentId, status, source, markedAt);
    }

    private LeaveView leave(UUID id, UUID studentId, LocalDate startsOn, LocalDate endsOn,
                            String reason, String status, OffsetDateTime createdAt) {
        return new LeaveView(id, studentId, startsOn, endsOn, reason, status, createdAt);
    }

    private <T> T found(List<T> rows, String resource) {
        if (rows.isEmpty()) {
            throw missing(resource);
        }
        return rows.getFirst();
    }

    private ResponseStatusException missing(String resource) {
        return new ResponseStatusException(NOT_FOUND, resource + " not found");
    }

    public record SessionView(UUID id, UUID classSectionId, UUID courseId, UUID teacherUserId,
                              OffsetDateTime startsAt, OffsetDateTime endsAt) {
    }

    public record AttendanceStudentView(UUID id, String enrollmentNo, String firstName, String lastName) {
    }

    public record RecordView(UUID id, UUID sessionId, UUID studentId, String status, String source,
                             OffsetDateTime markedAt) {
    }

    public record LeaveView(UUID id, UUID studentId, LocalDate startsOn, LocalDate endsOn,
                            String reason, String status, OffsetDateTime createdAt) {
    }
}
