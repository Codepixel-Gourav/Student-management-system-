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

public final class FinanceRequests {
    private FinanceRequests() {
    }

    public record Invoice(
            @NotNull UUID studentId,
            @NotBlank @Size(max = 60) String invoiceNo,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal subtotal,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal penalty,
            @NotNull LocalDate dueOn) {
    }

    public record Payment(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotBlank @Pattern(regexp = "INITIATED|PENDING|SUCCEEDED|FAILED|REFUNDED") String status,
            @Size(max = 180) String providerReference,
            @NotBlank @Size(max = 120) String idempotencyKey) {
    }

    public record PaymentUpdate(
            @NotBlank @Pattern(regexp = "INITIATED|PENDING|SUCCEEDED|FAILED|REFUNDED") String status,
            @Size(max = 180) String providerReference) {
    }
}
