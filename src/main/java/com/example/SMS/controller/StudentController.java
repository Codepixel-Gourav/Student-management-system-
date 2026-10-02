package com.example.SMS.controller;

import com.example.SMS.dto.CreateStudentRequest;
import com.example.SMS.entity.Student;
import com.example.SMS.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    public Page<Student> getStudents(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy) {
        return studentService.getStudents(tenantId, page, size, sortBy);
    }

    @PostMapping
    public ResponseEntity<Student> createStudent(@Valid @RequestBody CreateStudentRequest request) {
        Student student = studentService.createStudent(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(student.getId())
                .toUri();
        return ResponseEntity.created(location).body(student);
    }

    @GetMapping("/{id}")
    public Student getStudent(@RequestParam UUID tenantId, @PathVariable UUID id) {
        return studentService.getStudent(tenantId, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(@RequestParam UUID tenantId, @PathVariable UUID id) {
        studentService.deleteStudent(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
