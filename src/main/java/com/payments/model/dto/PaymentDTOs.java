package com.payments.model.dto;

import com.payments.model.enums.CurrencyCode;
import com.payments.model.enums.PaymentMethod;
import com.payments.model.enums.PaymentStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Input/Output DTOs used by GraphQL resolvers.
 * Using Java records for immutability and conciseness.
 */
public final class PaymentDTOs {

    private PaymentDTOs() {}

    // ─── Inputs ─────────────────────────────────────────────────────────────

    public record CreateAccountInput(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 2, max = 255) String firstName,
            @NotBlank @Size(min = 2, max = 255) String lastName,
            @NotNull CurrencyCode currency,
            @NotBlank @Size(max = 100) String userId
    ) {}

    public record InitiatePaymentInput(
            @NotNull UUID senderId,
            @NotNull UUID recipientId,
            @NotNull @DecimalMin("0.01") @DecimalMax("50000.00") BigDecimal amount,
            @NotNull CurrencyCode currency,
            @NotNull PaymentMethod method,
            @Size(max = 500) String description,
            @Size(max = 2000) String metadata,
            @NotBlank @Size(max = 128) String idempotencyKey
    ) {}

    public record RefundPaymentInput(
            @NotNull UUID paymentId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 128) String idempotencyKey
    ) {}

    public record AddPaymentMethodInput(
            @NotNull UUID accountId,
            @NotNull PaymentMethod type,
            @NotBlank String token,
            boolean isDefault
    ) {}

    public record PaymentFilterInput(
            PaymentStatus status,
            PaymentMethod method,
            CurrencyCode currency,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Instant fromDate,
            Instant toDate,
            UUID senderId,
            UUID recipientId
    ) {}

    public record PaymentSortInput(
            String field,
            String direction
    ) {
        public PaymentSortInput {
            field = field != null ? field : "createdAt";
            direction = direction != null ? direction : "DESC";
        }
    }

    // ─── Outputs ─────────────────────────────────────────────────────────────

    public record PaymentResult(
            boolean success,
            Object payment,
            java.util.List<FieldError> errors,
            String message
    ) {
        public static PaymentResult success(Object payment) {
            return new PaymentResult(true, payment, null, "Payment processed successfully");
        }

        public static PaymentResult failure(String message, java.util.List<FieldError> errors) {
            return new PaymentResult(false, null, errors, message);
        }

        public static PaymentResult failure(String message) {
            return new PaymentResult(false, null, null, message);
        }
    }

    public record RefundResult(
            boolean success,
            Object refund,
            java.util.List<FieldError> errors,
            String message
    ) {
        public static RefundResult success(Object refund) {
            return new RefundResult(true, refund, null, "Refund processed successfully");
        }

        public static RefundResult failure(String message) {
            return new RefundResult(false, null, null, message);
        }
    }

    public record FieldError(String field, String message, String code) {}

    public record PaymentStats(
            BigDecimal totalVolume,
            int totalCount,
            double successRate,
            BigDecimal averageAmount,
            CurrencyCode currency,
            String period
    ) {}

    public record PageInfo(
            int totalElements,
            int totalPages,
            int currentPage,
            int pageSize,
            boolean hasNext,
            boolean hasPrevious
    ) {}
}
