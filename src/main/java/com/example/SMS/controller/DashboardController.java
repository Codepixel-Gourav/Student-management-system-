package com.example.SMS.controller;

import com.example.SMS.dto.DashboardSummary;
import com.example.SMS.service.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import com.example.SMS.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER')")
public class DashboardController {
    private final StudentService studentService;

    public DashboardController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/summary")
    public DashboardSummary getSummary(@AuthenticationPrincipal AuthenticatedUser user) {
        return studentService.getDashboardSummary(user.tenantId());
    }
}
