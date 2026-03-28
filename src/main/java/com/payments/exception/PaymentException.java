package com.payments.exception;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ─── Base Exception ───────────────────────────────────────────────────────────
@Getter
public abstract class PaymentException extends RuntimeException implements GraphQLError {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Map<String, Object> extensions;

    protected PaymentException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.extensions = Map.of(
                "code", errorCode,
                "httpStatus", httpStatus.value()
        );
    }

    protected PaymentException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.extensions = Map.of(
                "code", errorCode,
                "httpStatus", httpStatus.value()
        );
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorClassification getErrorType() {
        return ErrorType.DataFetchingException;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return extensions;
    }
}

// ─── Concrete Exceptions ──────────────────────────────────────────────────────

class DuplicateResourceException extends PaymentException {
    public DuplicateResourceException(String message) {
        super(message, "DUPLICATE_RESOURCE", HttpStatus.CONFLICT);
    }
}

class IdempotencyConflictException extends PaymentException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("A different request with idempotency key '" + idempotencyKey + "' already exists",
                "IDEMPOTENCY_CONFLICT", HttpStatus.CONFLICT);
    }
}

class InsufficientFundsException extends PaymentException {
    public InsufficientFundsException(UUID accountId) {
        super("Insufficient funds in account: " + accountId,
                "INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

class InvalidPaymentStateException extends PaymentException {
    public InvalidPaymentStateException(String message) {
        super(message, "INVALID_PAYMENT_STATE", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

class AccountSuspendedException extends PaymentException {
    public AccountSuspendedException(UUID accountId) {
        super("Account " + accountId + " is not active",
                "ACCOUNT_SUSPENDED", HttpStatus.FORBIDDEN);
    }
}

class PaymentLimitExceededException extends PaymentException {
    public PaymentLimitExceededException(String message) {
        super(message, "PAYMENT_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

class RefundAmountExceededException extends PaymentException {
    public RefundAmountExceededException(String message) {
        super(message, "REFUND_AMOUNT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

class CurrencyMismatchException extends PaymentException {
    public CurrencyMismatchException(String message) {
        super(message, "CURRENCY_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

class RateLimitException extends PaymentException {
    public RateLimitException() {
        super("Rate limit exceeded. Please try again later.",
                "RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS);
    }
}

class KycNotVerifiedException extends PaymentException {
    public KycNotVerifiedException(UUID accountId) {
        super("Account " + accountId + " has not completed KYC verification",
                "KYC_NOT_VERIFIED", HttpStatus.FORBIDDEN);
    }
}
