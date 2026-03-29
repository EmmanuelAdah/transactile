package com.payments.service.impl;

import com.payments.exception.Exceptions;
import com.payments.model.dto.PaymentDTOs.*;
import com.payments.model.entity.*;
import com.payments.model.enums.*;
import com.payments.repository.*;
import com.payments.service.AuditService;
import com.payments.service.PaymentService;
import com.payments.service.RiskService;
import com.payments.validation.PaymentValidator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final RefundRepository refundRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    private final RiskService riskService;
    private final PaymentValidator paymentValidator;

    @Value("${app.payment.processing-fee-percent:0.029}")
    private double processingFeePercent;

    @Value("${app.payment.fixed-fee:0.30}")
    private double fixedFee;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "paymentProcessor", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentProcessor")
    public Payment initiatePayment(InitiatePaymentDTO input) {
        log.info("Initiating payment: idempotencyKey={}, amount={} {}",
                input.idempotencyKey(), input.amount(), input.currency());

        // ── 1. Idempotency check ──────────────────────────────────────────────
        paymentRepository.findByIdempotencyKey(input.idempotencyKey())
                .ifPresent(existing -> {
                    log.info("Returning existing payment for idempotency key: {}", input.idempotencyKey());
                    throw new IdempotencyReturnException(existing);
                });

        // ── 2. Load accounts with pessimistic lock ────────────────────────────
        Account sender = accountRepository.findByIdForUpdate(input.senderId())
                .orElseThrow(() -> Exceptions.notFound("Account", input.senderId()));
        Account recipient = accountRepository.findByIdForUpdate(input.recipientId())
                .orElseThrow(() -> Exceptions.notFound("Account", input.recipientId()));

        // ── 3. Validate ───────────────────────────────────────────────────────
        paymentValidator.validatePayment(sender, recipient, input);

        // ── 4. Risk assessment ────────────────────────────────────────────────
        double riskScore = riskService.assessRisk(sender, input.amount(), input.currency());
        if (riskScore > 0.9) {
            throw Exceptions.invalidState("Payment blocked by fraud detection");
        }

        // ── 5. Calculate fees ─────────────────────────────────────────────────
        BigDecimal processingFee = calculateFee(input.amount());
        BigDecimal netAmount = input.amount().subtract(processingFee);

        // ── 6. Create payment ─────────────────────────────────────────────────
        Payment payment = Payment.builder()
                .referenceId(generateReferenceId())
                .amount(input.amount())
                .currency(input.currency())
                .method(input.method())
                .description(input.description())
                .metadata(input.metadata())
                .idempotencyKey(input.idempotencyKey())
                .processingFee(processingFee)
                .netAmount(netAmount)
                .riskScore(riskScore)
                .sender(sender)
                .recipient(recipient)
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);

        // ── 7. Hold funds ─────────────────────────────────────────────────────
        sender.hold(input.amount());
        accountRepository.save(sender);

        // ── 8. Record transactions ────────────────────────────────────────────
        recordTransaction(payment, sender, TransactionType.PAYMENT,
                input.amount().negate(), "Payment hold: " + payment.getReferenceId());

        auditService.log("PAYMENT_INITIATED", "Payment", payment.getId(),
                sender.getId(), "amount=" + input.amount() + " " + input.currency());

        log.info("Payment initiated: id={}, referenceId={}", payment.getId(), payment.getReferenceId());
        return payment;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payment confirmPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> Exceptions.notFound("Payment", paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw Exceptions.invalidState("Payment is not in PENDING state: " + payment.getStatus());
        }

        Payment finalPayment = payment;
        Account sender = accountRepository.findByIdForUpdate(payment.getSender().getId())
                .orElseThrow(() -> Exceptions.notFound("Account", finalPayment.getSender().getId()));
        Account recipient = accountRepository.findByIdForUpdate(payment.getRecipient().getId())
                .orElseThrow(() -> Exceptions.notFound("Account", finalPayment.getRecipient().getId()));

        // Transition to PROCESSING then COMPLETED
        payment.transitionTo(PaymentStatus.PROCESSING);
        payment = paymentRepository.save(payment);

        // Debit sender fully
        sender.debit(payment.getAmount());
        accountRepository.save(sender);

        // Credit recipient net amount
        recipient.credit(payment.getNetAmount());
        accountRepository.save(recipient);

        payment.transitionTo(PaymentStatus.COMPLETED);
        payment = paymentRepository.save(payment);

        // Record debit and credit transactions
        recordTransaction(payment, sender, TransactionType.PAYMENT,
                payment.getAmount().negate(), "Payment debit: " + payment.getReferenceId());
        recordTransaction(payment, recipient, TransactionType.PAYMENT,
                payment.getNetAmount(), "Payment credit: " + payment.getReferenceId());

        auditService.log("PAYMENT_COMPLETED", "Payment", payment.getId(),
                sender.getId(), "netAmount=" + payment.getNetAmount());

        log.info("Payment confirmed: id={}", payment.getId());
        return payment;
    }

    @Override
    @Transactional
    public Payment cancelPayment(UUID paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> Exceptions.notFound("Payment", paymentId));

        if (!payment.getStatus().canTransitionTo(PaymentStatus.CANCELLED)) {
            throw Exceptions.invalidState("Cannot cancel payment in state: " + payment.getStatus());
        }

        Payment finalPayment = payment;
        Account sender = accountRepository.findByIdForUpdate(payment.getSender().getId())
                .orElseThrow(() -> Exceptions.notFound("Account", finalPayment.getSender().getId()));

        // Release hold
        sender.releaseHold(payment.getAmount());
        accountRepository.save(sender);

        payment.setFailureReason(reason);
        payment.transitionTo(PaymentStatus.CANCELLED);
        payment = paymentRepository.save(payment);

        auditService.log("PAYMENT_CANCELLED", "Payment", payment.getId(),
                sender.getId(), "reason=" + reason);

        return payment;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Refund refundPayment(RefundPaymentDTO input) {
        log.info("Processing refund for payment: {}, amount: {}", input.paymentId(), input.amount());

        // Idempotency check
        if (refundRepository.existsByIdempotencyKey(input.idempotencyKey())) {
            return refundRepository.findByIdempotencyKey(input.idempotencyKey())
                    .orElseThrow();
        }

        Payment payment = paymentRepository.findByIdWithDetails(input.paymentId())
                .orElseThrow(() -> Exceptions.notFound("Payment", input.paymentId()));

        if (!payment.canBeRefunded()) {
            throw Exceptions.invalidState("Payment cannot be refunded: status=" + payment.getStatus());
        }

        BigDecimal availableForRefund = payment.getAmount().subtract(payment.getRefundedAmount());
        if (input.amount().compareTo(availableForRefund) > 0) {
            throw Exceptions.refundExceeded(
                    "Refund amount " + input.amount() + " exceeds available " + availableForRefund);
        }

        Account recipient = accountRepository.findByIdForUpdate(payment.getRecipient().getId())
                .orElseThrow();
        Account sender = accountRepository.findByIdForUpdate(payment.getSender().getId())
                .orElseThrow();

        Refund refund = Refund.builder()
                .amount(input.amount())
                .currency(payment.getCurrency())
                .reason(input.reason())
                .idempotencyKey(input.idempotencyKey())
                .payment(payment)
                .initiatedBy(recipient) // merchant initiates refund
                .status(PaymentStatus.PENDING)
                .build();

        refund = refundRepository.save(refund);

        // Debit recipient
        recipient.debit(input.amount());
        accountRepository.save(recipient);

        // Credit sender
        sender.credit(input.amount());
        accountRepository.save(sender);

        // Update refund and payment status
        refund.setStatus(PaymentStatus.COMPLETED);
        refund.setProcessedAt(Instant.now());
        refund = refundRepository.save(refund);

        if (payment.isFullyRefunded()) {
            payment.transitionTo(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        paymentRepository.save(payment);

        recordTransaction(payment, sender, TransactionType.REFUND,
                input.amount(), "Refund: " + refund.getId());

        auditService.log("REFUND_PROCESSED", "Refund", refund.getId(),
                recipient.getId(), "amount=" + input.amount());

        return refund;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(UUID id) {
        return paymentRepository.findByIdWithDetails(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByReferenceId(String referenceId) {
        return paymentRepository.findByReferenceId(referenceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Payment> findAll(PaymentFilterDTO filter, PaymentSortInput sort, Pageable pageable) {
        Specification<Payment> spec = buildSpecification(filter);
        Sort sortOrder = Sort.by(
                "ASC".equalsIgnoreCase(sort.direction()) ? Sort.Direction.ASC : Sort.Direction.DESC,
                sanitizeSortField(sort.field())
        );
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortOrder);
        return paymentRepository.findAll(spec, sortedPageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStats getStats(UUID accountId, CurrencyCode currency, String period) {
        Instant since = parsePeriod(period);
        Object[] raw = paymentRepository.getPaymentStats(accountId, currency, since);

        if (raw == null || raw[0] == null) {
            return new PaymentStats(BigDecimal.ZERO, 0, 0.0, BigDecimal.ZERO, currency, period);
        }

        long total = ((Number) raw[0]).longValue();
        BigDecimal volume = raw[1] != null ? (BigDecimal) raw[1] : BigDecimal.ZERO;
        BigDecimal avg = raw[2] != null ? (BigDecimal) raw[2] : BigDecimal.ZERO;
        long successful = raw[3] != null ? ((Number) raw[3]).longValue() : 0;

        double successRate = total > 0 ? (double) successful / total * 100 : 0.0;

        return new PaymentStats(volume, (int) total, successRate, avg, currency, period);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private BigDecimal calculateFee(BigDecimal amount) {
        BigDecimal percentFee = amount.multiply(BigDecimal.valueOf(processingFeePercent));
        return percentFee.add(BigDecimal.valueOf(fixedFee)).setScale(4, RoundingMode.HALF_UP);
    }

    private String generateReferenceId() {
        return "PAY-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void recordTransaction(Payment payment, Account account, TransactionType type,
                                   BigDecimal amount, String description) {
        Transaction tx = Transaction.builder()
                .type(type)
                .amount(amount.abs())
                .currency(payment.getCurrency())
                .balanceBefore(account.getBalance().subtract(amount))
                .balanceAfter(account.getBalance())
                .payment(payment)
                .account(account)
                .description(description)
                .build();
        transactionRepository.save(tx);
    }

    private Specification<Payment> buildSpecification(PaymentFilterDTO filter) {
        if (filter == null) return Specification.where(null);

        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (filter.status() != null)
                predicates.add(cb.equal(root.get("status"), filter.status()));
            if (filter.method() != null)
                predicates.add(cb.equal(root.get("method"), filter.method()));
            if (filter.currency() != null)
                predicates.add(cb.equal(root.get("currency"), filter.currency()));
            if (filter.minAmount() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            if (filter.maxAmount() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            if (filter.fromDate() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.fromDate()));
            if (filter.toDate() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.toDate()));
            if (filter.senderId() != null)
                predicates.add(cb.equal(root.get("sender").get("id"), filter.senderId()));
            if (filter.recipientId() != null)
                predicates.add(cb.equal(root.get("recipient").get("id"), filter.recipientId()));

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private String sanitizeSortField(String field) {
        return switch (field) {
            case "amount", "status", "method", "currency", "createdAt", "updatedAt" -> field;
            default -> "createdAt";
        };
    }

    private Instant parsePeriod(String period) {
        return switch (period.toLowerCase()) {
            case "today" -> Instant.now().truncatedTo(ChronoUnit.DAYS);
            case "week" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "month" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "year" -> Instant.now().minus(365, ChronoUnit.DAYS);
            default -> Instant.now().minus(30, ChronoUnit.DAYS);
        };
    }

    // Fallback for circuit breaker
    private Payment paymentFallback(InitiatePaymentDTO input, Throwable t) {
        log.error("Circuit breaker triggered for payment initiation: {}", t.getMessage());
        throw Exceptions.invalidState("Payment processing is temporarily unavailable. Please try again later.");
    }

    // Internal exception for idempotency returns
    public static class IdempotencyReturnException extends RuntimeException {
        private final Payment payment;

        IdempotencyReturnException(Payment payment) {
            super("Idempotent return");
            this.payment = payment;
        }

        Payment getPayment() {
            return payment;
        }
    }
}
