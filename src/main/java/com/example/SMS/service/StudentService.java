package com.example.SMS.service;

import com.example.SMS.dto.CreateStudentRequest;
import com.example.SMS.dto.DashboardSummary;
import com.example.SMS.dto.MonthlyEnrollmentCount;
import com.example.SMS.dto.UpdateStudentRequest;
import com.example.SMS.entity.Student;
import com.example.SMS.repository.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.example.SMS.security.AuthenticatedUser;

@Service
@Transactional(readOnly = true)
public class StudentService {
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("firstName", "lastName", "email", "enrollmentNo", "enrollmentYear", "createdAt");

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Page<Student> getStudents(
            UUID tenantId, int page, int size, String sortBy, String direction, String search) {
        String normalizedSearch = search == null ? "" : search.trim();
        if (page < 0 || size < 1 || size > 100 || !SORTABLE_FIELDS.contains(sortBy)
                || direction == null
                || !Set.of("ASC", "DESC").contains(direction.toUpperCase(Locale.ROOT))
                || normalizedSearch.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination or sort parameters");
        }
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        return studentRepository.searchByTenantId(tenantId, normalizedSearch, pageable);
    }

    public Student getStudent(UUID tenantId, UUID id) {
        return studentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    public DashboardSummary getDashboardSummary(UUID tenantId) {
        OffsetDateTime start = Year.now(ZoneOffset.UTC).minusYears(1).atDay(1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = Year.now(ZoneOffset.UTC).plusYears(1).atDay(1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        List<MonthlyEnrollmentCount> monthlyEnrollments = studentRepository
                .countMonthlyEnrollments(tenantId, start, end)
                .stream()
                .map(row -> new MonthlyEnrollmentCount(row.getYear(), row.getMonth(), row.getCount()))
                .toList();
        long total = studentRepository.countByTenantId(tenantId);
        return new DashboardSummary(
                total,
                studentRepository.countByTenantIdAndAdmissionStatus(tenantId, "ENROLLED"),
                studentRepository.countByTenantIdAndAdmissionStatus(tenantId, "APPLIED"),
                monthlyEnrollments);
    }

    @Transactional
    public Student createStudent(AuthenticatedUser user, CreateStudentRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(user.tenantId());
        student.setCampusId(request.campusId());
        student.setEnrollmentNo(request.enrollmentNo().trim());
        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setEmail(request.email() == null ? null : request.email().trim().toLowerCase());
        student.setDepartment(request.department());
        student.setEnrollmentYear(request.enrollmentYear());
        student.setDateOfBirth(request.dateOfBirth());
        student.setAdmissionStatus("APPLIED");
        student.setCreatedAt(now);
        student.setUpdatedAt(now);
        return studentRepository.save(student);
    }

    @Transactional
    public Student updateStudent(UUID tenantId, UUID id, UpdateStudentRequest request) {
        Student student = getStudent(tenantId, id);
        student.setEnrollmentNo(request.enrollmentNo().trim());
        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setEmail(request.email() == null ? null : request.email().trim().toLowerCase());
        student.setDepartment(request.department() == null ? null : request.department().trim());
        student.setEnrollmentYear(request.enrollmentYear());
        student.setDateOfBirth(request.dateOfBirth());
        if (request.admissionStatus() != null) {
            student.setAdmissionStatus(request.admissionStatus());
        }
        student.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return studentRepository.save(student);
    }

    @Transactional
    public void deleteStudent(UUID tenantId, UUID id) {
        if (!studentRepository.existsByIdAndTenantId(id, tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
        }
        studentRepository.deleteByIdAndTenantId(id, tenantId);
    }
}
