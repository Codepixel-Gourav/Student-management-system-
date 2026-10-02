package com.example.SMS.controller;

import com.example.SMS.dto.AcademicRequests;
import com.example.SMS.security.AuthenticatedUser;
import com.example.SMS.service.AcademicService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER')")
public class AcademicController {
    private final AcademicService service;

    public AcademicController(AcademicService service) {
        this.service = service;
    }

    @GetMapping("/academic-periods")
    public List<AcademicService.PeriodView> periods(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.periods(user.tenantId());
    }

    @PostMapping("/academic-periods")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AcademicService.PeriodView createPeriod(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AcademicRequests.Period request) {
        return service.createPeriod(user.tenantId(), request);
    }

    @PutMapping("/academic-periods/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AcademicService.PeriodView updatePeriod(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody AcademicRequests.Period request) {
        return service.updatePeriod(user.tenantId(), id, request);
    }

    @DeleteMapping("/academic-periods/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public void deletePeriod(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deletePeriod(user.tenantId(), id);
    }

    @GetMapping("/courses")
    public List<AcademicService.CourseView> courses(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.courses(user.tenantId());
    }

    @PostMapping("/courses")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AcademicService.CourseView createCourse(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AcademicRequests.Course request) {
        return service.createCourse(user.tenantId(), request);
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AcademicService.CourseView updateCourse(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody AcademicRequests.Course request) {
        return service.updateCourse(user.tenantId(), id, request);
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public void deleteCourse(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deleteCourse(user.tenantId(), id);
    }

    @GetMapping("/class-sections")
    public List<AcademicService.SectionView> sections(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.sections(user.tenantId());
    }

    @PostMapping("/class-sections")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AcademicService.SectionView createSection(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AcademicRequests.Section request) {
        return service.createSection(user.tenantId(), request);
    }

    @PutMapping("/class-sections/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AcademicService.SectionView updateSection(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody AcademicRequests.Section request) {
        return service.updateSection(user.tenantId(), id, request);
    }

    @DeleteMapping("/class-sections/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public void deleteSection(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deleteSection(user.tenantId(), id);
    }

    @GetMapping("/settings/campuses")
    public List<AcademicService.CampusView> campuses(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.campuses(user.tenantId());
    }

    @PostMapping("/settings/campuses")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AcademicService.CampusView createCampus(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AcademicRequests.Campus request) {
        return service.createCampus(user.tenantId(), request);
    }

    @PutMapping("/settings/campuses/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public AcademicService.CampusView updateCampus(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody AcademicRequests.Campus request) {
        return service.updateCampus(user.tenantId(), id, request);
    }

    @DeleteMapping("/settings/campuses/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public void deleteCampus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deleteCampus(user.tenantId(), id);
    }
}
