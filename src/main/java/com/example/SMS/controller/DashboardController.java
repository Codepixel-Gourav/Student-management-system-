package com.example.SMS.controller;

import com.example.SMS.dto.DashboardSummary;
import com.example.SMS.service.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final StudentService studentService;

    public DashboardController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/summary")
    public DashboardSummary getSummary(@RequestParam UUID tenantId) {
        return studentService.getDashboardSummary(tenantId);
    }
}
