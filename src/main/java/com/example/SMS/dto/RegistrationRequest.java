package com.example.SMS.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank @Size(max = 180) String tenantName,
        @NotBlank @Size(min = 3, max = 80)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Tenant slug must contain lowercase letters, numbers, and single hyphens only")
        String tenantSlug,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 200) String password,
        @NotBlank @Size(max = 180) String displayName) {
}
