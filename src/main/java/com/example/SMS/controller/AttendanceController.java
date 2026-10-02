package com.example.SMS.controller;

import com.example.SMS.dto.AttendanceRequests;
import com.example.SMS.security.AuthenticatedUser;
import com.example.SMS.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER')")
public class AttendanceController {
    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping("/sessions")
    public List<AttendanceService.SessionView> sessions(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.listSessions(user.tenantId());
    }

    @GetMapping("/sessions/{id}")
    public AttendanceService.SessionView session(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.getSession(user.tenantId(), id);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AttendanceService.SessionView createSession(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AttendanceRequests.Session request) {
        return service.createSession(user.tenantId(), request);
    }

    @PutMapping("/sessions/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AttendanceService.SessionView updateSession(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody AttendanceRequests.Session request) {
        return service.updateSession(user.tenantId(), id, request);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public void deleteSession(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deleteSession(user.tenantId(), id);
    }

    @GetMapping("/sessions/{sessionId}/records")
    public List<AttendanceService.RecordView> records(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sessionId) {
        return service.records(user.tenantId(), sessionId);
    }

    @GetMapping("/sessions/{sessionId}/students")
    public List<AttendanceService.AttendanceStudentView> eligibleStudents(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sessionId) {
        return service.eligibleStudents(user.tenantId(), sessionId);
    }

    @PostMapping("/sessions/{sessionId}/records")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER')")
    public AttendanceService.RecordView createRecord(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sessionId,
            @Valid @RequestBody AttendanceRequests.Record request) {
        return service.createRecord(user.tenantId(), sessionId, request);
    }

    @GetMapping("/records/{id}")
    public AttendanceService.RecordView record(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.getRecord(user.tenantId(), id);
    }

    @PutMapping("/records/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER')")
    public AttendanceService.RecordView updateRecord(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody AttendanceRequests.RecordUpdate request) {
        return service.updateRecord(user.tenantId(), id, request.status());
    }

    @DeleteMapping("/records/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public void deleteRecord(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deleteRecord(user.tenantId(), id);
    }

    @GetMapping("/leave-requests")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public List<AttendanceService.LeaveView> leaveRequests(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.leaveRequests(user.tenantId());
    }

    @GetMapping("/leave-requests/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AttendanceService.LeaveView leaveRequest(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.getLeaveRequest(user.tenantId(), id);
    }

    @PostMapping("/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AttendanceService.LeaveView createLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AttendanceRequests.LeaveRequest request) {
        return service.createLeaveRequest(user.tenantId(), request);
    }

    @PutMapping("/leave-requests/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AttendanceService.LeaveView updateLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody AttendanceRequests.LeaveRequest request) {
        return service.updateLeaveRequest(user.tenantId(), id, request);
    }

    @DeleteMapping("/leave-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public void deleteLeaveRequest(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deleteLeaveRequest(user.tenantId(), id);
    }
}
