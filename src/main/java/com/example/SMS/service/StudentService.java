package com.example.SMS.service;

import com.example.SMS.dto.CreateStudentRequest;
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
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StudentService {
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("firstName", "lastName", "email", "enrollmentNo", "enrollmentYear", "createdAt");

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Page<Student> getStudents(UUID tenantId, int page, int size, String sortBy) {
        if (page < 0 || size < 1 || size > 100 || !SORTABLE_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination or sort parameters");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());
        return studentRepository.findAllByTenantId(tenantId, pageable);
    }

    public Student getStudent(UUID tenantId, UUID id) {
        return studentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    @Transactional
    public Student createStudent(CreateStudentRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(request.tenantId());
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
    public void deleteStudent(UUID tenantId, UUID id) {
        if (!studentRepository.existsByIdAndTenantId(id, tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
        }
        studentRepository.deleteByIdAndTenantId(id, tenantId);
    }
}
