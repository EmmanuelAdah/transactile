package com.payments.model;

import com.payments.model.entity.Account;
import com.payments.model.entity.Payment;
import com.payments.model.entity.Refund;
import com.payments.model.enums.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Domain Model Unit Tests")
class DomainModelTest {

    // ─── PaymentStatus transitions ────────────────────────────────────────────

    @Nested
    @DisplayName("PaymentStatus transitions")
    class PaymentStatusTransitionTests {

        @Test
        void pendingCanTransitionToProcessing() {
            assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
        }

        @Test
        void pendingCanBeCancelled() {
            assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.CANCELLED)).isTrue();
        }

        @Test
        void processingCanCompleteOrFail() {
            assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.COMPLETED)).isTrue();
            assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        }

        @Test
        void completedCanBeRefunded() {
            assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
            assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.PARTIALLY_REFUNDED)).isTrue();
        }

        @Test
        void terminalStatusCannotTransition() {
            assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.COMPLETED)).isFalse();
            assertThat(PaymentStatus.CANCELLED.canTransitionTo(PaymentStatus.PROCESSING)).isFalse();
            assertThat(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.COMPLETED)).isFalse();
        }

        @Test
        void isTerminalReturnsTrueForFinalStates() {
            assertThat(PaymentStatus.COMPLETED.isTerminal()).isTrue();
            assertThat(PaymentStatus.FAILED.isTerminal()).isTrue();
            assertThat(PaymentStatus.CANCELLED.isTerminal()).isTrue();
            assertThat(PaymentStatus.REFUNDED.isTerminal()).isTrue();
            assertThat(PaymentStatus.PENDING.isTerminal()).isFalse();
            assertThat(PaymentStatus.PROCESSING.isTerminal()).isFalse();
        }
    }

    // ─── Account balance operations ───────────────────────────────────────────

    @Nested
    @DisplayName("Account balance operations")
    class AccountBalanceTests {

        private Account account;

        @BeforeEach
        void setUp() {
            account = Account.builder()
                    .id(UUID.randomUUID())
                    .balance(new BigDecimal("1000.00"))
                    .availableBalance(new BigDecimal("1000.00"))
                    .build();
        }

        @Test
        void creditIncreasesBalance() {
            account.credit(new BigDecimal("500.00"));
            assertThat(account.getBalance()).isEqualByComparingTo("1500.00");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("1500.00");
        }

        @Test
        void debitDecreasesBalance() {
            account.debit(new BigDecimal("300.00"));
            assertThat(account.getBalance()).isEqualByComparingTo("700.00");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("700.00");
        }

        @Test
        void holdReducesAvailableButNotBalance() {
            account.hold(new BigDecimal("400.00"));
            assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("600.00");
        }

        @Test
        void releaseHoldRestoresAvailable() {
            account.hold(new BigDecimal("400.00"));
            account.releaseHold(new BigDecimal("400.00"));
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("1000.00");
        }

        @Test
        void creditWithNegativeAmountThrows() {
            assertThatThrownBy(() -> account.credit(new BigDecimal("-100.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        void debitMoreThanAvailableThrows() {
            assertThatThrownBy(() -> account.debit(new BigDecimal("2000.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Insufficient");
        }

        @Test
        void holdMoreThanAvailableThrows() {
            assertThatThrownBy(() -> account.hold(new BigDecimal("2000.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Insufficient");
        }
    }

    // ─── Payment domain logic ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Payment domain logic")
    class PaymentDomainTests {

        private Payment payment;

        @BeforeEach
        void setUp() {
            payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .amount(new BigDecimal("500.00"))
                    .currency(CurrencyCode.USD)
                    .status(PaymentStatus.COMPLETED)
                    .sender(Account.builder().id(UUID.randomUUID()).build())
                    .recipient(Account.builder().id(UUID.randomUUID()).build())
                    .build();
        }

        @Test
        void canBeRefundedWhenCompletedWithNoRefunds() {
            assertThat(payment.canBeRefunded()).isTrue();
        }

        @Test
        void cannotBeRefundedWhenAlreadyRefunded() {
            Refund fullRefund = Refund.builder()
                    .amount(new BigDecimal("500.00"))
                    .status(PaymentStatus.COMPLETED)
                    .build();
            payment.getRefunds().add(fullRefund);

            assertThat(payment.isFullyRefunded()).isTrue();
            assertThat(payment.canBeRefunded()).isFalse();
        }

        @Test
        void canBePartiallyRefundedWhenSomeRemains() {
            Refund partial = Refund.builder()
                    .amount(new BigDecimal("200.00"))
                    .status(PaymentStatus.COMPLETED)
                    .build();
            payment.getRefunds().add(partial);

            assertThat(payment.isFullyRefunded()).isFalse();
            assertThat(payment.getRefundedAmount()).isEqualByComparingTo("200.00");
            assertThat(payment.canBeRefunded()).isTrue();
        }

        @Test
        void transitionSetsCompletedAt() {
            Payment pending = Payment.builder()
                    .id(UUID.randomUUID())
                    .status(PaymentStatus.PENDING)
                    .build();
            pending.transitionTo(PaymentStatus.PROCESSING);
            pending.transitionTo(PaymentStatus.COMPLETED);

            assertThat(pending.getCompletedAt()).isNotNull();
            assertThat(pending.getCompletedAt()).isBeforeOrEqualTo(Instant.now());
        }

        @Test
        void invalidTransitionThrows() {
            assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.PROCESSING))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition");
        }
    }
}
