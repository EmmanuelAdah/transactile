package com.payments.service;

import com.payments.model.entity.Account;
import com.payments.model.enums.CurrencyCode;
import com.payments.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskService {

    private final PaymentRepository paymentRepository;

    @Value("${app.payment.daily-limit:100000}")
    private BigDecimal dailyLimit;

    @Value("${app.payment.max-transaction-amount:50000}")
    private BigDecimal maxTransactionAmount;

    /**
     * Returns a risk score between 0.0 (low risk) and 1.0 (high risk).
     * In production this would integrate with a dedicated fraud detection service.
     */
    @Transactional(readOnly = true)
    public double assessRisk(Account sender, BigDecimal amount, CurrencyCode currency) {
        double score = 0.0;

        // Factor 1: Transaction amount relative to limit
        if (amount.compareTo(maxTransactionAmount) > 0) {
            score += 0.4;
        } else if (amount.compareTo(maxTransactionAmount.multiply(BigDecimal.valueOf(0.5))) > 0) {
            score += 0.1;
        }

        // Factor 2: Daily volume check
        Instant dayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        BigDecimal dailyVolume = paymentRepository.sumAmountBySenderSince(sender.getId(), currency, dayStart);
        if (dailyVolume != null && dailyVolume.add(amount).compareTo(dailyLimit) > 0) {
            score += 0.35;
        }

        // Factor 3: Account age (new accounts are higher risk)
        long accountAgeDays = ChronoUnit.DAYS.between(sender.getCreatedAt(), Instant.now());
        if (accountAgeDays < 7) {
            score += 0.25;
        } else if (accountAgeDays < 30) {
            score += 0.1;
        }

        // Factor 4: KYC verification
        if (!sender.isKycVerified()) {
            score += 0.1;
        }

        double finalScore = Math.min(score, 1.0);
        log.debug("Risk score for account {}: {}", sender.getId(), finalScore);
        return finalScore;
    }
}
