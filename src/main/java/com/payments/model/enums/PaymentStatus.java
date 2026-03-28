package com.payments.model.enums;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    DISPUTED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == REFUNDED;
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING -> next == PROCESSING || next == CANCELLED;
            case PROCESSING -> next == COMPLETED || next == FAILED;
            case COMPLETED -> next == REFUNDED || next == PARTIALLY_REFUNDED || next == DISPUTED;
            default -> false;
        };
    }
}
