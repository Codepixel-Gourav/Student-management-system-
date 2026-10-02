package com.example.SMS.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateStudentRequest(
        @NotBlank @Size(max = 60) String enrollmentNo,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 254) String email,
        @Size(max = 120) String department,
        @Min(1900) @Max(2200) Integer enrollmentYear,
        LocalDate dateOfBirth,
        @Pattern(regexp = "APPLIED|UNDER_REVIEW|ADMITTED|ENROLLED|REJECTED|WITHDRAWN")
        String admissionStatus) {
}
