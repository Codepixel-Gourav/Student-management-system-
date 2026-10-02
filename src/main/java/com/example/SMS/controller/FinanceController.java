package com.example.SMS.controller;

import com.example.SMS.dto.FinanceRequests;
import com.example.SMS.security.AuthenticatedUser;
import com.example.SMS.service.FinanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
public class FinanceController {
    private final FinanceService service;

    public FinanceController(FinanceService service) {
        this.service = service;
    }

    @GetMapping("/invoices")
    public List<FinanceService.InvoiceView> invoices(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.invoices(user.tenantId());
    }

    @GetMapping("/invoices/{id}")
    public FinanceService.InvoiceView invoice(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.invoice(user.tenantId(), id);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceService.InvoiceView createInvoice(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody FinanceRequests.Invoice request) {
        return service.createInvoice(user.tenantId(), request);
    }

    @PutMapping("/invoices/{id}")
    public FinanceService.InvoiceView updateInvoice(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody FinanceRequests.Invoice request) {
        return service.updateInvoice(user.tenantId(), id, request);
    }

    @PostMapping("/invoices/{id}/void")
    public FinanceService.InvoiceView voidInvoice(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.voidInvoice(user.tenantId(), id);
    }

    @DeleteMapping("/invoices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInvoice(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deleteInvoice(user.tenantId(), id);
    }

    @GetMapping("/payments")
    public List<FinanceService.PaymentView> payments(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.payments(user.tenantId());
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public List<FinanceService.PaymentView> paymentsForInvoice(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID invoiceId) {
        return service.paymentsForInvoice(user.tenantId(), invoiceId);
    }

    @GetMapping("/payments/{id}")
    public FinanceService.PaymentView payment(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.payment(user.tenantId(), id);
    }

    @PostMapping("/invoices/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceService.PaymentView createPayment(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID invoiceId,
            @Valid @RequestBody FinanceRequests.Payment request) {
        return service.createPayment(user.tenantId(), invoiceId, request);
    }

    @PutMapping("/payments/{id}")
    public FinanceService.PaymentView updatePayment(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody FinanceRequests.PaymentUpdate request) {
        return service.updatePayment(user.tenantId(), id, request);
    }

    @DeleteMapping("/payments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deletePayment(user.tenantId(), id);
    }
}
