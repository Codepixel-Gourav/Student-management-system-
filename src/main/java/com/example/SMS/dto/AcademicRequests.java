package com.example.SMS.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class AcademicRequests {
    private AcademicRequests() {
    }

    public record Period(
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn,
            @Pattern(regexp = "PLANNED|ACTIVE|CLOSED") String status) {
    }

    public record Course(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 180) String name,
            @NotNull @DecimalMin("0.0") @Digits(integer = 3, fraction = 1) BigDecimal credits) {
    }

    public record Section(
            @NotNull UUID campusId,
            @NotNull UUID academicPeriodId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 40) String gradeLevel,
            @Size(max = 80) String room) {
    }

    public record Campus(
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 80) String timezone) {
    }
}
