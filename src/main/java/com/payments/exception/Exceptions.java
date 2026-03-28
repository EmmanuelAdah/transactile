package com.payments.exception;

import java.util.UUID;

public final class Exceptions {

    private Exceptions() {}

    public static ResourceNotFoundException notFound(String resource, UUID id) {
        return new ResourceNotFoundException(resource, id);
    }

    public static ResourceNotFoundException notFound(String resource, String field, String value) {
        return new ResourceNotFoundException(resource, field, value);
    }

    public static DuplicateResourceException duplicate(String message) {
        return new DuplicateResourceException(message);
    }

    public static IdempotencyConflictException idempotencyConflict(String key) {
        return new IdempotencyConflictException(key);
    }

    public static InsufficientFundsException insufficientFunds(UUID accountId) {
        return new InsufficientFundsException(accountId);
    }

    public static InvalidPaymentStateException invalidState(String message) {
        return new InvalidPaymentStateException(message);
    }

    public static AccountSuspendedException accountSuspended(UUID accountId) {
        return new AccountSuspendedException(accountId);
    }

    public static PaymentLimitExceededException limitExceeded(String message) {
        return new PaymentLimitExceededException(message);
    }

    public static RefundAmountExceededException refundExceeded(String message) {
        return new RefundAmountExceededException(message);
    }

    public static CurrencyMismatchException currencyMismatch(String message) {
        return new CurrencyMismatchException(message);
    }

    public static RateLimitException rateLimit() {
        return new RateLimitException();
    }

    public static KycNotVerifiedException kycNotVerified(UUID accountId) {
        return new KycNotVerifiedException(accountId);
    }
}
