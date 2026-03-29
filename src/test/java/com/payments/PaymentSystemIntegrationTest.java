package com.payments;

import com.payments.model.dto.PaymentDTOs.*;
import com.payments.model.entity.Account;
import com.payments.model.enums.*;
import com.payments.repository.AccountRepository;
import com.payments.repository.PaymentRepository;
import com.payments.service.AccountService;
import com.payments.service.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Full Integration Tests")
@Transactional
class PaymentSystemIntegrationTest {

    @Autowired private AccountService accountService;
    @Autowired private PaymentService paymentService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        alice = accountService.createAccount(new CreateAccountDTO(
                "alice+" + UUID.randomUUID() + "@test.com",
                "Alice Integration", CurrencyCode.USD, "EXT-INT-A-" + UUID.randomUUID()
        ));
        bob = accountService.createAccount(new CreateAccountDTO(
                "bob+" + UUID.randomUUID() + "@test.com",
                "Bob Integration", CurrencyCode.USD, "EXT-INT-B-" + UUID.randomUUID()
        ));

        // Fund Alice's account directly for testing
        alice = accountRepository.findById(alice.getId()).orElseThrow();
        alice.credit(new BigDecimal("10000.00"));
        accountRepository.save(alice);
    }

    @Test
    @DisplayName("full payment lifecycle: initiate → confirm → refund")
    void fullPaymentLifecycle() {
        String idempotencyKey = "integ-" + UUID.randomUUID();

        // ── 1. Initiate ───────────────────────────────────────────────────────
        var initiateInput = new InitiatePaymentDTO(
                alice.getId(), bob.getId(),
                new BigDecimal("500.00"), CurrencyCode.USD,
                PaymentMethod.CREDIT_CARD, "Integration test payment", null,
                idempotencyKey
        );

        var payment = paymentService.initiatePayment(initiateInput);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("500.00");
        assertThat(payment.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // Sender's available balance should be reduced
        Account aliceAfterHold = accountRepository.findById(alice.getId()).orElseThrow();
        assertThat(aliceAfterHold.getAvailableBalance())
                .isEqualByComparingTo("9500.00");

        // ── 2. Confirm ────────────────────────────────────────────────────────
        var confirmed = paymentService.confirmPayment(payment.getId());

        assertThat(confirmed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(confirmed.getCompletedAt()).isNotNull();

        Account aliceAfterPay = accountRepository.findById(alice.getId()).orElseThrow();
        Account bobAfterPay = accountRepository.findById(bob.getId()).orElseThrow();

        assertThat(aliceAfterPay.getBalance()).isEqualByComparingTo("9500.00");
        assertThat(bobAfterPay.getBalance())
                .isEqualByComparingTo(confirmed.getNetAmount()); // net after fees

        // ── 3. Refund ─────────────────────────────────────────────────────────
        var refundInput = new RefundPaymentDTO(
                payment.getId(),
                new BigDecimal("200.00"),
                "Partial refund requested",
                "refund-integ-" + UUID.randomUUID()
        );

        var refund = paymentService.refundPayment(refundInput);

        assertThat(refund.getAmount()).isEqualByComparingTo("200.00");
        assertThat(refund.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        Account aliceFinal = accountRepository.findById(alice.getId()).orElseThrow();
        assertThat(aliceFinal.getBalance()).isEqualByComparingTo("9700.00");

        // Payment should be PARTIALLY_REFUNDED
        var finalPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    @DisplayName("idempotency: duplicate initiation returns same payment")
    void idempotentPaymentInitiation() {
        String idempotencyKey = "idem-integ-" + UUID.randomUUID();
        var input = new InitiatePaymentDTO(
                alice.getId(), bob.getId(),
                new BigDecimal("100.00"), CurrencyCode.USD,
                PaymentMethod.BANK_TRANSFER, "Idempotent test", null,
                idempotencyKey
        );

        var first = paymentService.initiatePayment(input);

        // Second call with same key should throw IdempotencyReturnException
        assertThatThrownBy(() -> paymentService.initiatePayment(input))
                .satisfies(ex -> {
                    if (ex instanceof com.payments.service.impl.PaymentServiceImpl.IdempotencyReturnException ire) {
                        assertThat(ire.getPayment().getId()).isEqualTo(first.getId());
                    }
                });
    }

    @Test
    @DisplayName("cancel: hold is released when payment is cancelled")
    void cancelReleasesHold() {
        alice = accountRepository.findById(alice.getId()).orElseThrow();
        BigDecimal balanceBefore = alice.getAvailableBalance();

        var payment = paymentService.initiatePayment(new InitiatePaymentDTO(
                alice.getId(), bob.getId(),
                new BigDecimal("300.00"), CurrencyCode.USD,
                PaymentMethod.CREDIT_CARD, null, null,
                "cancel-integ-" + UUID.randomUUID()
        ));

        paymentService.cancelPayment(payment.getId(), "Test cancellation");

        Account aliceAfter = accountRepository.findById(alice.getId()).orElseThrow();
        assertThat(aliceAfter.getAvailableBalance()).isEqualByComparingTo(balanceBefore);
    }

    @Test
    @DisplayName("account creation: duplicate email is rejected")
    void duplicateEmailRejected() {
        String uniqueEmail = "dup-test-" + UUID.randomUUID() + "@test.com";
        accountService.createAccount(new CreateAccountDTO(
                uniqueEmail, "First User", CurrencyCode.USD, "EXT-DUP-1-" + UUID.randomUUID()
        ));

        assertThatThrownBy(() -> accountService.createAccount(
                new CreateAccountDTO(uniqueEmail, "Second User", CurrencyCode.USD, "EXT-DUP-2-" + UUID.randomUUID())
        )).hasMessageContaining("already exists");
    }
}
