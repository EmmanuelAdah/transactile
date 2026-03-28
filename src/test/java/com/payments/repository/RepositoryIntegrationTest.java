package com.payments.repository;

import com.payments.model.entity.Account;
import com.payments.model.entity.Payment;
import com.payments.model.enums.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Repository Integration Tests")
class RepositoryIntegrationTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        alice = accountRepository.save(Account.builder()
                .externalId("EXT-ALICE")
                .email("alice@test.com")
                .fullName("Alice Test")
                .status(AccountStatus.ACTIVE)
                .currency(CurrencyCode.USD)
                .balance(new BigDecimal("10000.00"))
                .availableBalance(new BigDecimal("10000.00"))
                .kycVerified(true)
                .build());

        bob = accountRepository.save(Account.builder()
                .externalId("EXT-BOB")
                .email("bob@test.com")
                .fullName("Bob Test")
                .status(AccountStatus.ACTIVE)
                .currency(CurrencyCode.USD)
                .balance(new BigDecimal("5000.00"))
                .availableBalance(new BigDecimal("5000.00"))
                .kycVerified(true)
                .build());
    }

    // ─── AccountRepository ────────────────────────────────────────────────────

    @Nested
    @DisplayName("AccountRepository")
    class AccountRepositoryTests {

        @Test
        void shouldFindByEmail() {
            Optional<Account> found = accountRepository.findByEmail("alice@test.com");
            assertThat(found).isPresent();
            assertThat(found.get().getFullName()).isEqualTo("Alice Test");
        }

        @Test
        void shouldReturnEmptyForUnknownEmail() {
            Optional<Account> found = accountRepository.findByEmail("nobody@test.com");
            assertThat(found).isEmpty();
        }

        @Test
        void shouldFindByExternalId() {
            Optional<Account> found = accountRepository.findByExternalId("EXT-BOB");
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("bob@test.com");
        }

        @Test
        void shouldDetectDuplicateEmail() {
            assertThat(accountRepository.existsByEmail("alice@test.com")).isTrue();
            assertThat(accountRepository.existsByEmail("unknown@test.com")).isFalse();
        }

        @Test
        void shouldUpdateStatus() {
            int updated = accountRepository.updateStatus(alice.getId(), AccountStatus.SUSPENDED);
            assertThat(updated).isEqualTo(1);

            // Re-fetch to verify
            Account refreshed = accountRepository.findById(alice.getId()).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        }

        @Test
        void shouldFindByIdForUpdate() {
            Optional<Account> locked = accountRepository.findByIdForUpdate(alice.getId());
            assertThat(locked).isPresent();
            assertThat(locked.get().getId()).isEqualTo(alice.getId());
        }
    }

    // ─── PaymentRepository ────────────────────────────────────────────────────

    @Nested
    @DisplayName("PaymentRepository")
    class PaymentRepositoryTests {

        private Payment savedPayment;

        @BeforeEach
        void createPayment() {
            Payment payment = Payment.builder()
                    .referenceId("PAY-REPO-001")
                    .amount(new BigDecimal("250.00"))
                    .currency(CurrencyCode.USD)
                    .status(PaymentStatus.COMPLETED)
                    .method(PaymentMethod.CREDIT_CARD)
                    .idempotencyKey("idem-repo-001")
                    .processingFee(new BigDecimal("7.55"))
                    .netAmount(new BigDecimal("242.45"))
                    .sender(alice)
                    .recipient(bob)
                    .build();
            savedPayment = paymentRepository.save(payment);
        }

        @Test
        void shouldFindByReferenceId() {
            Optional<Payment> found = paymentRepository.findByReferenceId("PAY-REPO-001");
            assertThat(found).isPresent();
            assertThat(found.get().getAmount()).isEqualByComparingTo("250.00");
        }

        @Test
        void shouldFindByIdempotencyKey() {
            Optional<Payment> found = paymentRepository.findByIdempotencyKey("idem-repo-001");
            assertThat(found).isPresent();
        }

        @Test
        void shouldCheckIdempotencyKeyExists() {
            assertThat(paymentRepository.existsByIdempotencyKey("idem-repo-001")).isTrue();
            assertThat(paymentRepository.existsByIdempotencyKey("non-existent")).isFalse();
        }

        @Test
        void shouldFindByAccountId() {
            Page<Payment> payments = paymentRepository
                    .findByAccountId(alice.getId(), PageRequest.of(0, 10));
            assertThat(payments.getContent()).hasSize(1);
            assertThat(payments.getContent().get(0).getReferenceId()).isEqualTo("PAY-REPO-001");
        }

        @Test
        void shouldFindByAccountIdAndStatus() {
            Page<Payment> completed = paymentRepository
                    .findByAccountIdAndStatus(alice.getId(), PaymentStatus.COMPLETED, PageRequest.of(0, 10));
            assertThat(completed.getContent()).hasSize(1);

            Page<Payment> pending = paymentRepository
                    .findByAccountIdAndStatus(alice.getId(), PaymentStatus.PENDING, PageRequest.of(0, 10));
            assertThat(pending.getContent()).isEmpty();
        }

        @Test
        void shouldFindWithDetails() {
            Optional<Payment> detailed = paymentRepository.findByIdWithDetails(savedPayment.getId());
            assertThat(detailed).isPresent();
            assertThat(detailed.get().getSender().getEmail()).isEqualTo("alice@test.com");
            assertThat(detailed.get().getRecipient().getEmail()).isEqualTo("bob@test.com");
        }

        @Test
        void shouldSumAmountBySenderSince() {
            BigDecimal sum = paymentRepository.sumAmountBySenderSince(
                    alice.getId(), CurrencyCode.USD, Instant.now().minusSeconds(86400));
            assertThat(sum).isEqualByComparingTo("250.00");
        }

        @Test
        void shouldFindAllWithPagination() {
            // Add a second payment
            Payment p2 = Payment.builder()
                    .referenceId("PAY-REPO-002")
                    .amount(new BigDecimal("50.00"))
                    .currency(CurrencyCode.USD)
                    .status(PaymentStatus.PENDING)
                    .method(PaymentMethod.BANK_TRANSFER)
                    .idempotencyKey("idem-repo-002")
                    .processingFee(new BigDecimal("1.75"))
                    .netAmount(new BigDecimal("48.25"))
                    .sender(bob)
                    .recipient(alice)
                    .build();
            paymentRepository.save(p2);

            Page<Payment> firstPage = paymentRepository.findAll(PageRequest.of(0, 1));
            assertThat(firstPage.getTotalElements()).isEqualTo(2);
            assertThat(firstPage.getTotalPages()).isEqualTo(2);
            assertThat(firstPage.getContent()).hasSize(1);
        }
    }
}
