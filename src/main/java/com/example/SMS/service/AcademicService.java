package com.example.SMS.service;

import com.example.SMS.dto.AcademicRequests;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.time.ZoneId;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class AcademicService {
    private final JdbcTemplate jdbc;

    public AcademicService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PeriodView> periods(UUID tenantId) {
        return jdbc.query("""
                SELECT id, name, starts_on, ends_on, status FROM academic_periods
                WHERE tenant_id = ? ORDER BY starts_on DESC, name
                """, (rs, row) -> new PeriodView(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
                rs.getString("status")), tenantId);
    }

    @Transactional
    public PeriodView createPeriod(UUID tenantId, AcademicRequests.Period request) {
        validatePeriod(request);
        String status = request.status() == null ? "PLANNED" : request.status();
        return jdbc.queryForObject("""
                INSERT INTO academic_periods (tenant_id, name, starts_on, ends_on, status)
                VALUES (?, ?, ?, ?, ?) RETURNING id, name, starts_on, ends_on, status
                """, (rs, row) -> new PeriodView(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
                rs.getString("status")), tenantId, request.name().trim(), request.startsOn(), request.endsOn(), status);
    }

    @Transactional
    public PeriodView updatePeriod(UUID tenantId, UUID id, AcademicRequests.Period request) {
        validatePeriod(request);
        String status = request.status() == null ? "PLANNED" : request.status();
        List<PeriodView> rows = jdbc.query("""
                UPDATE academic_periods SET name = ?, starts_on = ?, ends_on = ?, status = ?
                WHERE tenant_id = ? AND id = ?
                RETURNING id, name, starts_on, ends_on, status
                """, (rs, row) -> new PeriodView(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
                rs.getString("status")), request.name().trim(), request.startsOn(), request.endsOn(), status, tenantId, id);
        return requireFound(rows, "Academic period");
    }

    @Transactional
    public void deletePeriod(UUID tenantId, UUID id) {
        if (jdbc.update("DELETE FROM academic_periods WHERE tenant_id = ? AND id = ?", tenantId, id) == 0) {
            throw missing("Academic period");
        }
    }

    public List<CourseView> courses(UUID tenantId) {
        return jdbc.query("""
                SELECT id, code, name, credits FROM courses WHERE tenant_id = ? ORDER BY code
                """, (rs, row) -> new CourseView(
                rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getBigDecimal("credits")), tenantId);
    }

    @Transactional
    public CourseView createCourse(UUID tenantId, AcademicRequests.Course request) {
        return jdbc.queryForObject("""
                INSERT INTO courses (tenant_id, code, name, credits) VALUES (?, ?, ?, ?)
                RETURNING id, code, name, credits
                """, (rs, row) -> course(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("name"), rs.getBigDecimal("credits")), tenantId, request.code().trim(),
                request.name().trim(), request.credits());
    }

    @Transactional
    public CourseView updateCourse(UUID tenantId, UUID id, AcademicRequests.Course request) {
        List<CourseView> rows = jdbc.query("""
                UPDATE courses SET code = ?, name = ?, credits = ? WHERE tenant_id = ? AND id = ?
                RETURNING id, code, name, credits
                """, (rs, row) -> course(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("name"), rs.getBigDecimal("credits")), request.code().trim(),
                request.name().trim(), request.credits(), tenantId, id);
        return requireFound(rows, "Course");
    }

    @Transactional
    public void deleteCourse(UUID tenantId, UUID id) {
        if (jdbc.update("DELETE FROM courses WHERE tenant_id = ? AND id = ?", tenantId, id) == 0) {
            throw missing("Course");
        }
    }

    public List<SectionView> sections(UUID tenantId) {
        return jdbc.query("""
                SELECT id, campus_id, academic_period_id, name, grade_level, room
                FROM class_sections WHERE tenant_id = ? ORDER BY grade_level, name
                """, (rs, row) -> new SectionView(
                rs.getObject("id", UUID.class), rs.getObject("campus_id", UUID.class),
                rs.getObject("academic_period_id", UUID.class), rs.getString("name"),
                rs.getString("grade_level"), rs.getString("room")), tenantId);
    }

    @Transactional
    public SectionView createSection(UUID tenantId, AcademicRequests.Section request) {
        List<SectionView> rows = jdbc.query("""
                INSERT INTO class_sections (tenant_id, campus_id, academic_period_id, name, grade_level, room)
                SELECT ?, ?, ?, ?, ?, ?
                WHERE EXISTS (SELECT 1 FROM campuses WHERE tenant_id = ? AND id = ?)
                  AND EXISTS (SELECT 1 FROM academic_periods WHERE tenant_id = ? AND id = ?)
                RETURNING id, campus_id, academic_period_id, name, grade_level, room
                """, (rs, row) -> section(rs.getObject("id", UUID.class),
                rs.getObject("campus_id", UUID.class), rs.getObject("academic_period_id", UUID.class),
                rs.getString("name"), rs.getString("grade_level"), rs.getString("room")),
                tenantId, request.campusId(), request.academicPeriodId(), request.name().trim(),
                request.gradeLevel().trim(), request.room(), tenantId, request.campusId(),
                tenantId, request.academicPeriodId());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Campus and academic period must belong to your tenant");
        }
        return rows.getFirst();
    }

    @Transactional
    public SectionView updateSection(UUID tenantId, UUID id, AcademicRequests.Section request) {
        List<SectionView> rows = jdbc.query("""
                UPDATE class_sections SET campus_id = ?, academic_period_id = ?, name = ?, grade_level = ?, room = ?
                WHERE tenant_id = ? AND id = ?
                  AND EXISTS (SELECT 1 FROM campuses WHERE tenant_id = ? AND id = ?)
                  AND EXISTS (SELECT 1 FROM academic_periods WHERE tenant_id = ? AND id = ?)
                RETURNING id, campus_id, academic_period_id, name, grade_level, room
                """, (rs, row) -> section(rs.getObject("id", UUID.class),
                rs.getObject("campus_id", UUID.class), rs.getObject("academic_period_id", UUID.class),
                rs.getString("name"), rs.getString("grade_level"), rs.getString("room")),
                request.campusId(), request.academicPeriodId(), request.name().trim(),
                request.gradeLevel().trim(), request.room(), tenantId, id,
                tenantId, request.campusId(), tenantId, request.academicPeriodId());
        if (rows.isEmpty()) {
            throw missing("Class section (or referenced campus/period)");
        }
        return rows.getFirst();
    }

    @Transactional
    public void deleteSection(UUID tenantId, UUID id) {
        if (jdbc.update("DELETE FROM class_sections WHERE tenant_id = ? AND id = ?", tenantId, id) == 0) {
            throw missing("Class section");
        }
    }

    public List<CampusView> campuses(UUID tenantId) {
        return jdbc.query("""
                SELECT id, name, timezone FROM campuses WHERE tenant_id = ? ORDER BY name
                """, (rs, row) -> new CampusView(rs.getObject("id", UUID.class),
                rs.getString("name"), rs.getString("timezone")), tenantId);
    }

    @Transactional
    public CampusView createCampus(UUID tenantId, AcademicRequests.Campus request) {
        validateTimezone(request.timezone());
        return jdbc.queryForObject("""
                INSERT INTO campuses (tenant_id, name, timezone) VALUES (?, ?, ?)
                RETURNING id, name, timezone
                """, (rs, row) -> campus(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("timezone")), tenantId, request.name().trim(), request.timezone().trim());
    }

    @Transactional
    public CampusView updateCampus(UUID tenantId, UUID id, AcademicRequests.Campus request) {
        validateTimezone(request.timezone());
        List<CampusView> rows = jdbc.query("""
                UPDATE campuses SET name = ?, timezone = ? WHERE tenant_id = ? AND id = ?
                RETURNING id, name, timezone
                """, (rs, row) -> campus(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("timezone")), request.name().trim(), request.timezone().trim(), tenantId, id);
        return requireFound(rows, "Campus");
    }

    @Transactional
    public void deleteCampus(UUID tenantId, UUID id) {
        if (jdbc.update("DELETE FROM campuses WHERE tenant_id = ? AND id = ?", tenantId, id) == 0) {
            throw missing("Campus");
        }
    }

    private void validatePeriod(AcademicRequests.Period request) {
        if (!request.endsOn().isAfter(request.startsOn())) {
            throw new ResponseStatusException(BAD_REQUEST, "Academic period end date must follow its start date");
        }
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (java.time.DateTimeException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Timezone must be a valid IANA/UTC zone identifier");
        }
    }

    private <T> T requireFound(List<T> rows, String resource) {
        if (rows.isEmpty()) {
            throw missing(resource);
        }
        return rows.getFirst();
    }

    private ResponseStatusException missing(String resource) {
        return new ResponseStatusException(NOT_FOUND, resource + " not found");
    }

    private CourseView course(UUID id, String code, String name, BigDecimal credits) {
        return new CourseView(id, code, name, credits);
    }

    private SectionView section(UUID id, UUID campusId, UUID periodId, String name, String grade, String room) {
        return new SectionView(id, campusId, periodId, name, grade, room);
    }

    private CampusView campus(UUID id, String name, String timezone) {
        return new CampusView(id, name, timezone);
    }

    public record PeriodView(UUID id, String name, LocalDate startsOn, LocalDate endsOn, String status) {
    }

    public record CourseView(UUID id, String code, String name, BigDecimal credits) {
    }

    public record SectionView(UUID id, UUID campusId, UUID academicPeriodId, String name, String gradeLevel, String room) {
    }

    public record CampusView(UUID id, String name, String timezone) {
    }
}
