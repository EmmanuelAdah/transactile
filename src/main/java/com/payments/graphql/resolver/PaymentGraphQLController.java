package com.payments.graphql.resolver;

import com.payments.exception.Exceptions;
import com.payments.model.dto.PaymentDTOs.*;
import com.payments.model.entity.*;
import com.payments.model.enums.CurrencyCode;
import com.payments.repository.TransactionRepository;
import com.payments.service.AccountService;
import com.payments.service.AuditService;
import com.payments.service.PaymentService;
import com.payments.service.impl.PaymentServiceImpl.IdempotencyReturnException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GraphQL Controller using Spring for GraphQL's annotation-based schema mapping.
 *
 * Annotations used:
 * - @QueryMapping     → maps to Query fields in schema
 * - @MutationMapping  → maps to Mutation fields in schema
 * - @SchemaMapping    → maps to type fields (nested resolvers)
 * - @Argument         → binds GraphQL arguments to method params
 * - @ContextValue     → extracts values from GraphQL context
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PaymentGraphQLController {

    private final PaymentService paymentService;
    private final AccountService accountService;
    private final AuditService auditService;
    private final TransactionRepository transactionRepository;

    // ─── Account Queries ──────────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Account account(@Argument UUID id) {
        return accountService.findById(id)
                .orElseThrow(() -> Exceptions.notFound("Account", id));
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Account accountByEmail(@Argument String email) {
        return accountService.findByEmail(email)
                .orElseThrow(() -> Exceptions.notFound("Account", "email", email));
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> accounts(@Argument int page, @Argument int size) {
        Page<Account> result = accountService.findAll(PageRequest.of(page, size));
        return toPageMap(result);
    }

    // ─── Payment Queries ──────────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Payment payment(@Argument UUID id) {
        return paymentService.findById(id)
                .orElseThrow(() -> Exceptions.notFound("Payment", id));
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Payment paymentByReference(@Argument String referenceId) {
        return paymentService.findByReferenceId(referenceId)
                .orElseThrow(() -> Exceptions.notFound("Payment", "referenceId", referenceId));
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> payments(
            @Argument PaymentFilterInput filter,
            @Argument PaymentSortInput sort,
            @Argument int page,
            @Argument int size) {

        var sortInput = sort != null ? sort : new PaymentSortInput("createdAt", "DESC");
        Page<Payment> result = paymentService.findAll(filter, sortInput, PageRequest.of(page, size));
        return toPageMap(result);
    }

    // ─── Analytics Queries ────────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public PaymentStats paymentStats(
            @Argument UUID accountId,
            @Argument CurrencyCode currency,
            @Argument String period) {
        return paymentService.getStats(accountId, currency, period);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<Transaction> transactions(
            @Argument UUID accountId,
            @Argument int page,
            @Argument int size) {
        return transactionRepository
                .findByAccountId(accountId, PageRequest.of(page, size))
                .getContent();
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<AuditLog> auditLogs(
            @Argument UUID entityId,
            @Argument int page,
            @Argument int size) {
        return auditService.getLogsForEntity(entityId, PageRequest.of(page, size)).getContent();
    }

    // ─── Account Mutations ────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Account createAccount(@Argument @Valid CreateAccountInput input) {
        return accountService.createAccount(input);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Account suspendAccount(@Argument UUID id, @Argument String reason) {
        return accountService.suspendAccount(id, reason);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Account reactivateAccount(@Argument UUID id) {
        return accountService.reactivateAccount(id);
    }

    // ─── Payment Mutations ────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public PaymentResult initiatePayment(@Argument @Valid InitiatePaymentInput input) {
        try {
            Payment payment = paymentService.initiatePayment(input);
            return PaymentResult.success(payment);
        } catch (IdempotencyReturnException e) {
            // Idempotent replay — return existing payment as success
            return PaymentResult.success(e.getPayment());
        } catch (Exception e) {
            log.warn("Payment initiation failed: {}", e.getMessage());
            return PaymentResult.failure(e.getMessage());
        }
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public PaymentResult confirmPayment(@Argument UUID paymentId) {
        try {
            Payment payment = paymentService.confirmPayment(paymentId);
            return PaymentResult.success(payment);
        } catch (Exception e) {
            return PaymentResult.failure(e.getMessage());
        }
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public PaymentResult cancelPayment(@Argument UUID paymentId, @Argument String reason) {
        try {
            Payment payment = paymentService.cancelPayment(paymentId, reason);
            return PaymentResult.success(payment);
        } catch (Exception e) {
            return PaymentResult.failure(e.getMessage());
        }
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public RefundResult refundPayment(@Argument @Valid RefundPaymentInput input) {
        try {
            Refund refund = paymentService.refundPayment(input);
            return RefundResult.success(refund);
        } catch (Exception e) {
            return RefundResult.failure(e.getMessage());
        }
    }

    // ─── Nested Schema Mappings ───────────────────────────────────────────────

    /**
     * @SchemaMapping resolves the `payments` field on the Account type.
     * typeName = "Account" matches the GraphQL type; field = "payments" matches the field name.
     */
    @SchemaMapping(typeName = "Account", field = "payments")
    public Map<String, Object> accountPayments(
            Account account,
            @Argument int page,
            @Argument int size) {
        Page<Payment> result = paymentService.findAll(
                new PaymentFilterInput(null, null, null, null, null, null, null,
                        account.getId(), null),
                new PaymentSortInput("createdAt", "DESC"),
                PageRequest.of(page, size));
        return toPageMap(result);
    }

    @SchemaMapping(typeName = "Payment", field = "transactions")
    public List<Transaction> paymentTransactions(Payment payment) {
        return transactionRepository.findByPaymentId(payment.getId());
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private <T> Map<String, Object> toPageMap(Page<T> page) {
        return Map.of(
                "content", page.getContent(),
                "totalElements", (int) page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "currentPage", page.getNumber(),
                "pageSize", page.getSize(),
                "hasNext", page.hasNext(),
                "hasPrevious", page.hasPrevious()
        );
    }
}
