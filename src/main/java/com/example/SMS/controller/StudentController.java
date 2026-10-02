package com.example.SMS.controller;

import com.example.SMS.dto.CreateStudentRequest;
import com.example.SMS.dto.UpdateStudentRequest;
import com.example.SMS.entity.Student;
import com.example.SMS.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;
import com.example.SMS.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/students")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER')")
public class StudentController {
    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    public Page<Student> getStudents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "") String search) {
        return studentService.getStudents(user.tenantId(), page, size, sortBy, direction, search);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Student> createStudent(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateStudentRequest request) {
        Student student = studentService.createStudent(user, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(student.getId())
                .toUri();
        return ResponseEntity.created(location).body(student);
    }

    @GetMapping("/{id}")
    public Student getStudent(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return studentService.getStudent(user.tenantId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public Student updateStudent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStudentRequest request) {
        return studentService.updateStudent(user.tenantId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteStudent(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        studentService.deleteStudent(user.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
