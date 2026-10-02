package com.example.SMS.service;

import com.example.SMS.dto.FinanceRequests;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class FinanceService {
    private final JdbcTemplate jdbc;

    public FinanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InvoiceView> invoices(UUID tenantId) {
        return jdbc.query(invoiceSelect() + """
                WHERE st.tenant_id = ? ORDER BY i.created_at DESC LIMIT 500
                """, (rs, row) -> invoice(rs), tenantId);
    }

    public InvoiceView invoice(UUID tenantId, UUID invoiceId) {
        return found(jdbc.query(invoiceSelect() + """
                WHERE st.tenant_id = ? AND i.id = ?
                """, (rs, row) -> invoice(rs), tenantId, invoiceId), "Invoice");
    }

    @Transactional
    public InvoiceView createInvoice(UUID tenantId, FinanceRequests.Invoice request) {
        List<InvoiceView> rows = jdbc.query("""
                INSERT INTO invoices (student_id, invoice_no, currency, subtotal, penalty, due_on, status)
                SELECT st.id, ?, ?, ?, ?, ?,
                       CASE WHEN ? < CURRENT_DATE THEN 'OVERDUE' ELSE 'PENDING' END
                FROM students st
                WHERE st.id = ? AND st.tenant_id = ?
                RETURNING id, student_id, invoice_no, currency, subtotal, penalty, total, due_on, status, created_at
                """, (rs, row) -> invoice(rs), request.invoiceNo().trim(), request.currency(),
                request.subtotal(), request.penalty(), request.dueOn(), request.dueOn(), request.studentId(), tenantId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Student must belong to your tenant");
        }
        return rows.getFirst();
    }

    @Transactional
    public InvoiceView updateInvoice(UUID tenantId, UUID invoiceId, FinanceRequests.Invoice request) {
        lockInvoice(tenantId, invoiceId);
        if (hasPayments(invoiceId)) {
            throw new ResponseStatusException(CONFLICT, "Invoices with payment history cannot be edited");
        }
        List<InvoiceView> rows = jdbc.query("""
                UPDATE invoices i SET student_id = ?, invoice_no = ?, currency = ?, subtotal = ?, penalty = ?,
                    due_on = ?, status = CASE WHEN i.status = 'VOID' THEN 'VOID'
                                             WHEN ? < CURRENT_DATE THEN 'OVERDUE' ELSE 'PENDING' END
                WHERE i.id = ? AND EXISTS (SELECT 1 FROM students owner
                                           WHERE owner.id = i.student_id AND owner.tenant_id = ?)
                  AND EXISTS (SELECT 1 FROM students target
                              WHERE target.id = ? AND target.tenant_id = ?)
                  AND i.status <> 'VOID'
                  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.invoice_id = i.id)
                RETURNING i.id, i.student_id, i.invoice_no, i.currency, i.subtotal, i.penalty,
                          i.total, i.due_on, i.status, i.created_at
                """, (rs, row) -> invoice(rs),
                request.studentId(), request.invoiceNo().trim(), request.currency(),
                request.subtotal(), request.penalty(), request.dueOn(), request.dueOn(),
                invoiceId, tenantId, request.studentId(), tenantId);
        return found(rows, "Invoice (invoices with payment history or voided invoices are immutable)");
    }

    @Transactional
    public InvoiceView voidInvoice(UUID tenantId, UUID invoiceId) {
        lockInvoice(tenantId, invoiceId);
        List<InvoiceView> rows = jdbc.query("""
                UPDATE invoices i SET status = 'VOID'
                WHERE i.id = ? AND EXISTS (SELECT 1 FROM students st
                                           WHERE st.id = i.student_id AND st.tenant_id = ?)
                  AND i.status <> 'VOID'
                  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.invoice_id = i.id)
                RETURNING i.id, i.student_id, i.invoice_no, i.currency, i.subtotal, i.penalty,
                          i.total, i.due_on, i.status, i.created_at
                """, (rs, row) -> invoice(rs), invoiceId, tenantId);
        return found(rows, "Invoice (not found or has settled payment history)");
    }

    @Transactional
    public void deleteInvoice(UUID tenantId, UUID invoiceId) {
        lockInvoice(tenantId, invoiceId);
        int deleted = jdbc.update("""
                DELETE FROM invoices i WHERE i.id = ?
                  AND EXISTS (SELECT 1 FROM students st WHERE st.id = i.student_id AND st.tenant_id = ?)
                  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.invoice_id = i.id)
                """, invoiceId, tenantId);
        if (deleted == 0) {
            boolean exists = !jdbc.query("""
                    SELECT i.id FROM invoices i JOIN students st ON st.id = i.student_id
                    WHERE st.tenant_id = ? AND i.id = ?
                    """, (rs, row) -> rs.getObject(1, UUID.class), tenantId, invoiceId).isEmpty();
            if (!exists) {
                throw missing("Invoice");
            }
            throw new ResponseStatusException(CONFLICT, "Invoices with payment history cannot be deleted");
        }
    }

    public List<PaymentView> payments(UUID tenantId) {
        return jdbc.query(paymentSelect() + """
                WHERE st.tenant_id = ? ORDER BY p.created_at DESC LIMIT 1000
                """, (rs, row) -> payment(rs), tenantId);
    }

    public List<PaymentView> paymentsForInvoice(UUID tenantId, UUID invoiceId) {
        invoice(tenantId, invoiceId);
        return jdbc.query(paymentSelect() + """
                WHERE st.tenant_id = ? AND i.id = ? ORDER BY p.created_at
                """, (rs, row) -> payment(rs), tenantId, invoiceId);
    }

    public PaymentView payment(UUID tenantId, UUID paymentId) {
        return found(jdbc.query(paymentSelect() + """
                WHERE st.tenant_id = ? AND p.id = ?
                """, (rs, row) -> payment(rs), tenantId, paymentId), "Payment");
    }

    @Transactional
    public PaymentView createPayment(UUID tenantId, UUID invoiceId, FinanceRequests.Payment request) {
        PaymentView existing = existingPaymentByKey(tenantId, request.idempotencyKey().trim());
        if (existing != null) {
            if (!"MANUAL".equals(existing.provider())
                    || !existing.invoiceId().equals(invoiceId)
                    || existing.amount().compareTo(request.amount()) != 0
                    || !existing.currency().equals(request.currency())
                    || !java.util.Objects.equals(existing.providerReference(), cleanReference(request.providerReference()))) {
                throw new ResponseStatusException(CONFLICT,
                        "Idempotency key has already been used for a different payment request");
            }
            return existing;
        }
        LockedInvoice invoice = lockInvoice(tenantId, invoiceId);
        if ("VOID".equals(invoice.status())) {
            throw new ResponseStatusException(CONFLICT, "Cannot pay a void invoice");
        }
        if (!invoice.currency().equals(request.currency())) {
            throw new ResponseStatusException(BAD_REQUEST, "Payment currency must match invoice currency");
        }
        validatePaymentStatus(request.status());
        BigDecimal currentlyPaid = paidTotal(invoiceId);
        if ("SUCCEEDED".equals(request.status())
                && currentlyPaid.add(request.amount()).compareTo(invoice.total()) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Successful payments cannot exceed the invoice balance");
        }
        List<PaymentView> rows = jdbc.query("""
                INSERT INTO payments (invoice_id, provider, provider_reference, amount, currency,
                                      status, idempotency_key, paid_at)
                VALUES (?, 'MANUAL', ?, ?, ?, ?, ?, CASE WHEN ? = 'SUCCEEDED' THEN now() ELSE NULL END)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING id, invoice_id, provider, provider_reference, amount, currency, status,
                          idempotency_key, paid_at, created_at
                """, (rs, row) -> payment(rs), invoiceId, cleanReference(request.providerReference()),
                request.amount(), request.currency(), request.status(), request.idempotencyKey().trim(),
                request.status());
        if (rows.isEmpty()) {
            PaymentView raced = existingPaymentByKey(tenantId, request.idempotencyKey().trim());
            if (raced == null
                    || !"MANUAL".equals(raced.provider())
                    || !raced.invoiceId().equals(invoiceId)
                    || raced.amount().compareTo(request.amount()) != 0
                    || !raced.currency().equals(request.currency())
                    || !java.util.Objects.equals(raced.providerReference(), cleanReference(request.providerReference()))) {
                throw new ResponseStatusException(CONFLICT,
                        "Idempotency key has already been used for a different payment request");
            }
            return raced;
        }
        recomputeInvoice(invoiceId);
        return rows.getFirst();
    }

    @Transactional
    public PaymentView updatePayment(UUID tenantId, UUID paymentId, FinanceRequests.PaymentUpdate request) {
        PaymentSnapshot payment = lockPayment(tenantId, paymentId);
        if (!"MANUAL".equals(payment.provider())) {
            throw new ResponseStatusException(CONFLICT,
                    "External-provider payments can only be changed by a verified provider integration");
        }
        validateTransition(payment.status(), request.status());
        LockedInvoice invoice = lockInvoice(tenantId, payment.invoiceId());
        if ("VOID".equals(invoice.status())) {
            throw new ResponseStatusException(CONFLICT, "A void invoice cannot have payment status changes");
        }
        if ("SUCCEEDED".equals(request.status())) {
            BigDecimal paid = paidTotal(payment.invoiceId());
            if (paid.add(payment.amount()).compareTo(invoice.total()) > 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Successful payments cannot exceed the invoice balance");
            }
        }
        List<PaymentView> rows = jdbc.query("""
                UPDATE payments p SET status = ?, provider_reference = ?,
                    paid_at = CASE WHEN ? = 'SUCCEEDED' THEN COALESCE(p.paid_at, now())
                                   WHEN ? = 'REFUNDED' THEN p.paid_at ELSE NULL END
                FROM invoices i JOIN students st ON st.id = i.student_id
                WHERE p.id = ? AND p.invoice_id = i.id AND st.tenant_id = ? AND p.provider = 'MANUAL'
                RETURNING p.id, p.invoice_id, p.provider, p.provider_reference, p.amount, p.currency,
                          p.status, p.idempotency_key, p.paid_at, p.created_at
                """, (rs, row) -> payment(rs), request.status(),
                cleanReference(request.providerReference()), request.status(), request.status(), paymentId, tenantId);
        if (rows.isEmpty()) {
            throw missing("Payment");
        }
        recomputeInvoice(payment.invoiceId());
        return rows.getFirst();
    }

    @Transactional
    public void deletePayment(UUID tenantId, UUID paymentId) {
        List<UUID> invoiceIds = jdbc.query("""
                SELECT p.invoice_id FROM payments p
                JOIN invoices i ON i.id = p.invoice_id
                JOIN students st ON st.id = i.student_id
                WHERE p.id = ? AND st.tenant_id = ? FOR UPDATE OF i
                """, (rs, row) -> rs.getObject(1, UUID.class), paymentId, tenantId);
        if (invoiceIds.isEmpty()) {
            throw missing("Payment");
        }
        int deleted = jdbc.update("""
                DELETE FROM payments p USING invoices i, students st
                WHERE p.id = ? AND p.invoice_id = i.id AND st.id = i.student_id AND st.tenant_id = ?
                  AND p.provider = 'MANUAL'
                  AND p.status IN ('INITIATED', 'PENDING', 'FAILED')
                """, paymentId, tenantId);
        if (deleted == 0) {
            throw new ResponseStatusException(CONFLICT,
                    "Settled or refunded payments cannot be deleted; transition them through the refund workflow");
        }
    }

    private LockedInvoice lockInvoice(UUID tenantId, UUID invoiceId) {
        List<LockedInvoice> rows = jdbc.query("""
                SELECT i.id, i.total, i.currency, i.status
                FROM invoices i JOIN students st ON st.id = i.student_id
                WHERE st.tenant_id = ? AND i.id = ? FOR UPDATE OF i
                """, (rs, row) -> new LockedInvoice(rs.getObject("id", UUID.class),
                rs.getBigDecimal("total"), rs.getString("currency").trim(), rs.getString("status")),
                tenantId, invoiceId);
        return found(rows, "Invoice");
    }

    private PaymentSnapshot lockPayment(UUID tenantId, UUID paymentId) {
        List<PaymentSnapshot> rows = jdbc.query("""
                SELECT p.id, p.invoice_id, p.amount, p.currency, p.status, p.provider
                FROM payments p JOIN invoices i ON i.id = p.invoice_id
                JOIN students st ON st.id = i.student_id
                WHERE st.tenant_id = ? AND p.id = ? FOR UPDATE OF i, p
                """, (rs, row) -> new PaymentSnapshot(rs.getObject("id", UUID.class),
                rs.getObject("invoice_id", UUID.class), rs.getBigDecimal("amount"),
                rs.getString("currency").trim(), rs.getString("status"), rs.getString("provider")),
                tenantId, paymentId);
        return found(rows, "Payment");
    }

    private boolean hasPayments(UUID invoiceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM payments WHERE invoice_id = ?)", Boolean.class, invoiceId));
    }

    private PaymentView existingPaymentByKey(UUID tenantId, String key) {
        List<PaymentView> rows = jdbc.query(paymentSelect() + """
                WHERE st.tenant_id = ? AND p.idempotency_key = ?
                """, (rs, row) -> payment(rs), tenantId, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private BigDecimal paidTotal(UUID invoiceId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(amount), 0) FROM payments
                WHERE invoice_id = ? AND status = 'SUCCEEDED'
                """, BigDecimal.class, invoiceId);
    }

    private void recomputeInvoice(UUID invoiceId) {
        jdbc.update("""
                UPDATE invoices i SET status = CASE
                    WHEN i.status = 'VOID' THEN 'VOID'
                    WHEN COALESCE((SELECT sum(p.amount) FROM payments p
                                   WHERE p.invoice_id = i.id AND p.status = 'SUCCEEDED'), 0) >= i.total THEN 'PAID'
                    WHEN COALESCE((SELECT sum(p.amount) FROM payments p
                                   WHERE p.invoice_id = i.id AND p.status = 'SUCCEEDED'), 0) > 0 THEN 'PARTIALLY_PAID'
                    WHEN i.due_on < CURRENT_DATE THEN 'OVERDUE' ELSE 'PENDING' END
                WHERE i.id = ?
                """, invoiceId);
    }

    private void validatePaymentStatus(String status) {
        if (!List.of("INITIATED", "PENDING", "SUCCEEDED", "FAILED").contains(status)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "New manual payment status must be INITIATED, PENDING, SUCCEEDED, or FAILED");
        }
    }

    private void validateTransition(String current, String next) {
        boolean valid = switch (current) {
            case "INITIATED" -> List.of("PENDING", "SUCCEEDED", "FAILED").contains(next);
            case "PENDING" -> List.of("SUCCEEDED", "FAILED").contains(next);
            case "FAILED" -> false;
            case "SUCCEEDED" -> "REFUNDED".equals(next);
            case "REFUNDED" -> false;
            default -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid payment status transition");
        }
    }

    private String cleanReference(String reference) {
        return reference == null || reference.isBlank() ? null : reference.trim();
    }

    private String invoiceSelect() {
        return """
                SELECT i.id, i.student_id, i.invoice_no, i.currency, i.subtotal, i.penalty,
                       i.total, i.due_on, i.status, i.created_at
                FROM invoices i JOIN students st ON st.id = i.student_id
                """;
    }

    private String paymentSelect() {
        return """
                SELECT p.id, p.invoice_id, p.provider, p.provider_reference, p.amount, p.currency,
                       p.status, p.idempotency_key, p.paid_at, p.created_at
                FROM payments p JOIN invoices i ON i.id = p.invoice_id
                JOIN students st ON st.id = i.student_id
                """;
    }

    private InvoiceView invoice(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new InvoiceView(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("invoice_no"), rs.getString("currency").trim(), rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("penalty"), rs.getBigDecimal("total"),
                rs.getObject("due_on", LocalDate.class), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private PaymentView payment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PaymentView(rs.getObject("id", UUID.class), rs.getObject("invoice_id", UUID.class),
                rs.getString("provider"), rs.getString("provider_reference"), rs.getBigDecimal("amount"),
                rs.getString("currency").trim(), rs.getString("status"), rs.getString("idempotency_key"),
                rs.getObject("paid_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class));
    }

    private <T> T found(List<T> rows, String resource) {
        if (rows.isEmpty()) {
            throw missing(resource);
        }
        return rows.getFirst();
    }

    private ResponseStatusException missing(String resource) {
        return new ResponseStatusException(NOT_FOUND, resource + " not found");
    }

    private record LockedInvoice(UUID id, BigDecimal total, String currency, String status) {
    }

    private record PaymentSnapshot(
            UUID id, UUID invoiceId, BigDecimal amount, String currency, String status, String provider) {
    }

    public record InvoiceView(UUID id, UUID studentId, String invoiceNo, String currency,
                              BigDecimal subtotal, BigDecimal penalty, BigDecimal total, LocalDate dueOn,
                              String status, OffsetDateTime createdAt) {
    }

    public record PaymentView(UUID id, UUID invoiceId, String provider, String providerReference,
                              BigDecimal amount, String currency, String status, String idempotencyKey,
                              OffsetDateTime paidAt, OffsetDateTime createdAt) {
    }
}
