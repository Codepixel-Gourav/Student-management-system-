package com.example.SMS.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class AttendanceRequests {
    private AttendanceRequests() {
    }

    public record Session(
            @NotNull UUID classSectionId,
            UUID courseId,
            UUID teacherUserId,
            @NotNull OffsetDateTime startsAt,
            @NotNull OffsetDateTime endsAt) {
    }

    public record Record(
            @NotNull UUID studentId,
            @NotBlank @Pattern(regexp = "PRESENT|ABSENT|LATE|EXCUSED") String status) {
    }

    public record RecordUpdate(
            @NotBlank @Pattern(regexp = "PRESENT|ABSENT|LATE|EXCUSED") String status) {
    }

    public record LeaveRequest(
            @NotNull UUID studentId,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn,
            @NotBlank @Size(max = 4000) String reason,
            @NotBlank @Pattern(regexp = "PENDING|TEACHER_APPROVED|APPROVED|REJECTED|CANCELLED") String status) {
    }
}
