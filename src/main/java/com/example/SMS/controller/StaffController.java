package com.example.SMS.controller;

import com.example.SMS.dto.StaffRequests;
import com.example.SMS.security.AuthenticatedUser;
import com.example.SMS.service.StaffService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
@Validated
public class StaffController {
    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping
    public List<StaffService.StaffView> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.tenantId());
    }

    @GetMapping("/{id}")
    public StaffService.StaffView get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.get(user.tenantId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffService.StaffView create(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody StaffRequests.Create request) {
        return service.create(user.tenantId(), request);
    }

    @PutMapping("/{id}")
    public StaffService.StaffView update(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody StaffRequests.Update request) {
        return service.update(user.tenantId(), id, request);
    }

    @PutMapping("/{id}/password")
    public StaffService.StaffView setPassword(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody PasswordRequest request) {
        return service.setPassword(user.tenantId(), id, request.password());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.tenantId(), id, user.id());
    }

    public record PasswordRequest(@NotBlank @Size(min = 12, max = 200) String password) {
    }
}
