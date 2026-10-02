package com.example.SMS.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StaffRequests {
    private StaffRequests() {
    }

    public record Create(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 200) String password,
            @NotBlank @Size(max = 180) String displayName) {
    }

    public record Update(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 180) String displayName,
            @NotNull @Pattern(regexp = "INVITED|ACTIVE|LOCKED|DISABLED") String status) {
    }
}
