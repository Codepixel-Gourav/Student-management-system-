package com.example.SMS.dto;

import java.util.List;

public record DashboardSummary(
        long totalStudents,
        long enrolledStudents,
        long pendingAdmissions,
        List<MonthlyEnrollmentCount> monthlyEnrollments) {
}
