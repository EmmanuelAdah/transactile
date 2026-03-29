package com.payments.validation;

import com.payments.exception.Exceptions;
import com.payments.model.dto.PaymentDTOs.InitiatePaymentDTO;
import com.payments.model.entity.Account;
import com.payments.model.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PaymentValidator {

    @Value("${app.payment.max-transaction-amount:50000}")
    private BigDecimal maxTransactionAmount;

    public void validatePayment(Account sender, Account recipient, InitiatePaymentDTO input) {
        // Sender must be active
        if (sender.getStatus() != AccountStatus.ACTIVE) {
            throw Exceptions.accountSuspended(sender.getId());
        }

        // Recipient must be active
        if (recipient.getStatus() != AccountStatus.ACTIVE) {
            throw Exceptions.accountSuspended(recipient.getId());
        }

        // Cannot pay yourself
        if (sender.getId().equals(recipient.getId())) {
            throw Exceptions.invalidState("Sender and recipient cannot be the same account");
        }

        // Currency must match a sender's account currency (or be explicitly supported)
        if (!sender.getCurrency().equals(input.currency())) {
            throw Exceptions.currencyMismatch(
                    "Payment currency " + input.currency() +
                    " does not match sender account currency " + sender.getCurrency());
        }

        // Amount must be positive and within limits
        if (input.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        if (input.amount().compareTo(maxTransactionAmount) > 0) {
            throw Exceptions.limitExceeded(
                    "Payment amount " + input.amount() + " exceeds maximum " + maxTransactionAmount);
        }

        // Sufficient available balance
        if (sender.getAvailableBalance().compareTo(input.amount()) < 0) {
            throw Exceptions.insufficientFunds(sender.getId());
        }

        // KYC for large transactions
        if (input.amount().compareTo(BigDecimal.valueOf(10_000)) > 0 && !sender.isKycVerified()) {
            throw Exceptions.kycNotVerified(sender.getId());
        }
    }
}
