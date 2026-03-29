package com.payments.service;

import com.payments.model.dto.PaymentDTOs.*;
import com.payments.model.entity.Account;
import com.payments.model.entity.Payment;
import com.payments.model.entity.Refund;
import com.payments.model.enums.*;
import com.payments.repository.*;
import com.payments.service.impl.PaymentServiceImpl;
import com.payments.validation.PaymentValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AuditService auditService;
    @Mock private RiskService riskService;
    @Mock private PaymentValidator paymentValidator;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Account sender;
    private Account recipient;

    @BeforeEach
    void setUp() {
        sender = Account.builder()
                .id(UUID.randomUUID())
                .email("sender@example.com")
                .fullName("Alice Sender")
                .status(AccountStatus.ACTIVE)
                .currency(CurrencyCode.USD)
                .balance(new BigDecimal("5000.00"))
                .availableBalance(new BigDecimal("5000.00"))
                .kycVerified(true)
                .createdAt(Instant.now().minusSeconds(86400 * 60)) // 60 days old
                .build();

        recipient = Account.builder()
                .id(UUID.randomUUID())
                .email("recipient@example.com")
                .fullName("Bob Recipient")
                .status(AccountStatus.ACTIVE)
                .currency(CurrencyCode.USD)
                .balance(new BigDecimal("1000.00"))
                .availableBalance(new BigDecimal("1000.00"))
                .kycVerified(true)
                .createdAt(Instant.now().minusSeconds(86400 * 60))
                .build();
    }

    // ─── initiatePayment ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("initiatePayment")
    class InitiatePaymentTests {

        @Test
        @DisplayName("should initiate a valid payment successfully")
        void shouldInitiatePaymentSuccessfully() {
            // Arrange
            var input = new InitiatePaymentDTO(
                    sender.getId(), recipient.getId(),
                    new BigDecimal("100.00"), CurrencyCode.USD,
                    PaymentMethod.CREDIT_CARD, "Test payment", null,
                    "idem-key-001"
            );

            Payment expectedPayment = Payment.builder()
                    .id(UUID.randomUUID())
                    .referenceId("PAY-123")
                    .amount(input.amount())
                    .currency(input.currency())
                    .method(input.method())
                    .idempotencyKey(input.idempotencyKey())
                    .status(PaymentStatus.PENDING)
                    .sender(sender)
                    .recipient(recipient)
                    .processingFee(new BigDecimal("3.2000"))
                    .netAmount(new BigDecimal("96.8000"))
                    .build();

            when(paymentRepository.findByIdempotencyKey("idem-key-001")).thenReturn(Optional.empty());
            when(accountRepository.findByIdForUpdate(sender.getId())).thenReturn(Optional.of(sender));
            when(accountRepository.findByIdForUpdate(recipient.getId())).thenReturn(Optional.of(recipient));
            when(riskService.assessRisk(any(), any(), any())).thenReturn(0.1);
            when(paymentRepository.save(any(Payment.class))).thenReturn(expectedPayment);
            doNothing().when(paymentValidator).validatePayment(any(), any(), any());

            // Act
            Payment result = paymentService.initiatePayment(input);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.getAmount()).isEqualByComparingTo("100.00");
            assertThat(result.getIdempotencyKey()).isEqualTo("idem-key-001");

            verify(paymentRepository).save(any(Payment.class));
            verify(accountRepository, times(2)).save(any(Account.class));
            verify(transactionRepository).save(any());
            verify(auditService).log(eq("PAYMENT_INITIATED"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should return existing payment for duplicate idempotency key")
        void shouldReturnExistingForIdempotentRequest() {
            Payment existing = Payment.builder()
                    .id(UUID.randomUUID())
                    .idempotencyKey("idem-key-dup")
                    .status(PaymentStatus.PENDING)
                    .sender(sender)
                    .recipient(recipient)
                    .amount(new BigDecimal("50.00"))
                    .build();

            when(paymentRepository.findByIdempotencyKey("idem-key-dup"))
                    .thenReturn(Optional.of(existing));

            var input = new InitiatePaymentDTO(
                    sender.getId(), recipient.getId(),
                    new BigDecimal("50.00"), CurrencyCode.USD,
                    PaymentMethod.BANK_TRANSFER, null, null,
                    "idem-key-dup"
            );

            // The idempotency exception is caught at the controller layer;
            // here we verify the repository is checked and the exception is thrown internally
            assertThatThrownBy(() -> paymentService.initiatePayment(input))
                    .isInstanceOf(PaymentServiceImpl.IdempotencyReturnException.class)
                    .satisfies(ex -> {
                        Payment returned = ((PaymentServiceImpl.IdempotencyReturnException) ex).getPayment();
                        assertThat(returned.getIdempotencyKey()).isEqualTo("idem-key-dup");
                    });
        }

        @Test
        @DisplayName("should block payment when risk score is too high")
        void shouldBlockHighRiskPayment() {
            var input = new InitiatePaymentDTO(
                    sender.getId(), recipient.getId(),
                    new BigDecimal("100.00"), CurrencyCode.USD,
                    PaymentMethod.CRYPTO, null, null, "idem-key-risk"
            );

            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(accountRepository.findByIdForUpdate(sender.getId())).thenReturn(Optional.of(sender));
            when(accountRepository.findByIdForUpdate(recipient.getId())).thenReturn(Optional.of(recipient));
            doNothing().when(paymentValidator).validatePayment(any(), any(), any());
            when(riskService.assessRisk(any(), any(), any())).thenReturn(0.95); // HIGH RISK

            assertThatThrownBy(() -> paymentService.initiatePayment(input))
                    .hasMessageContaining("fraud detection");
        }

        @Test
        @DisplayName("should throw when sender account not found")
        void shouldThrowWhenSenderNotFound() {
            var input = new InitiatePaymentDTO(
                    UUID.randomUUID(), recipient.getId(),
                    BigDecimal.TEN, CurrencyCode.USD,
                    PaymentMethod.CREDIT_CARD, null, null, "idem-key-nf"
            );

            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(accountRepository.findByIdForUpdate(input.senderId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.initiatePayment(input))
                    .hasMessageContaining("not found");
        }
    }

    // ─── confirmPayment ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmPayment")
    class ConfirmPaymentTests {

        @Test
        @DisplayName("should confirm a PENDING payment and transfer funds")
        void shouldConfirmPayment() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .status(PaymentStatus.PENDING)
                    .amount(new BigDecimal("200.00"))
                    .currency(CurrencyCode.USD)
                    .processingFee(new BigDecimal("6.10"))
                    .netAmount(new BigDecimal("193.90"))
                    .sender(sender)
                    .recipient(recipient)
                    .build();

            when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
            when(accountRepository.findByIdForUpdate(sender.getId())).thenReturn(Optional.of(sender));
            when(accountRepository.findByIdForUpdate(recipient.getId())).thenReturn(Optional.of(recipient));
            when(paymentRepository.save(any())).thenReturn(payment);
            when(accountRepository.save(any())).thenReturn(sender);

            paymentService.confirmPayment(payment.getId());

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(accountRepository, atLeast(2)).save(any(Account.class));
            verify(auditService).log(eq("PAYMENT_COMPLETED"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw when confirming a non-PENDING payment")
        void shouldThrowWhenNotPending() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .status(PaymentStatus.COMPLETED)
                    .sender(sender)
                    .recipient(recipient)
                    .build();

            when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.confirmPayment(payment.getId()))
                    .hasMessageContaining("not in PENDING state");
        }
    }

    // ─── cancelPayment ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelPayment")
    class CancelPaymentTests {

        @Test
        @DisplayName("should cancel a PENDING payment and release hold")
        void shouldCancelPendingPayment() {
            BigDecimal holdAmount = new BigDecimal("300.00");
            BigDecimal originalAvailable = sender.getAvailableBalance();
            sender.hold(holdAmount); // simulate existing hold

            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .status(PaymentStatus.PENDING)
                    .amount(holdAmount)
                    .sender(sender)
                    .recipient(recipient)
                    .build();

            when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
            when(accountRepository.findByIdForUpdate(sender.getId())).thenReturn(Optional.of(sender));
            when(paymentRepository.save(any())).thenReturn(payment);

            paymentService.cancelPayment(payment.getId(), "Customer request");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(sender.getAvailableBalance()).isEqualByComparingTo(originalAvailable);
        }

        @Test
        @DisplayName("should throw when cancelling a COMPLETED payment")
        void shouldThrowWhenCompletedCannotBeCancelled() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .status(PaymentStatus.COMPLETED)
                    .sender(sender)
                    .recipient(recipient)
                    .build();

            when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.cancelPayment(payment.getId(), "Too late"))
                    .hasMessageContaining("Cannot cancel");
        }
    }

    // ─── refundPayment ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refundPayment")
    class RefundPaymentTests {

        @Test
        @DisplayName("should process full refund on completed payment")
        void shouldProcessFullRefund() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .status(PaymentStatus.COMPLETED)
                    .amount(new BigDecimal("150.00"))
                    .currency(CurrencyCode.USD)
                    .sender(sender)
                    .recipient(recipient)
                    .build();

            recipient.credit(new BigDecimal("150.00")); // recipient got paid

            var input = new RefundPaymentDTO(
                    payment.getId(), new BigDecimal("150.00"), "Defective product", "ref-idem-001"
            );

            when(refundRepository.existsByIdempotencyKey("ref-idem-001")).thenReturn(false);
            when(paymentRepository.findByIdWithDetails(payment.getId())).thenReturn(Optional.of(payment));
            when(accountRepository.findByIdForUpdate(recipient.getId())).thenReturn(Optional.of(recipient));
            when(accountRepository.findByIdForUpdate(sender.getId())).thenReturn(Optional.of(sender));

            Refund savedRefund = Refund.builder()
                    .id(UUID.randomUUID())
                    .amount(input.amount())
                    .status(PaymentStatus.COMPLETED)
                    .payment(payment)
                    .initiatedBy(recipient)
                    .build();

            when(refundRepository.save(any())).thenReturn(savedRefund);
            when(paymentRepository.save(any())).thenReturn(payment);

            Refund result = paymentService.refundPayment(input);

            assertThat(result).isNotNull();
            assertThat(result.getAmount()).isEqualByComparingTo("150.00");
            verify(auditService).log(eq("REFUND_PROCESSED"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw when refund amount exceeds payment amount")
        void shouldThrowWhenRefundExceedsPayment() {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .status(PaymentStatus.COMPLETED)
                    .amount(new BigDecimal("100.00"))
                    .currency(CurrencyCode.USD)
                    .sender(sender)
                    .recipient(recipient)
                    .build();

            var input = new RefundPaymentDTO(
                    payment.getId(), new BigDecimal("999.00"), "Over-refund", "ref-idem-002"
            );

            when(refundRepository.existsByIdempotencyKey(any())).thenReturn(false);
            when(paymentRepository.findByIdWithDetails(payment.getId())).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(input))
                    .hasMessageContaining("exceeds available");
        }

        @Test
        @DisplayName("should return existing refund for duplicate idempotency key")
        void shouldReturnExistingRefundIdempotently() {
            var input = new RefundPaymentDTO(
                    UUID.randomUUID(), new BigDecimal("50.00"), "Duplicate", "ref-idem-dup"
            );

            Refund existing = Refund.builder()
                    .id(UUID.randomUUID())
                    .amount(input.amount())
                    .idempotencyKey("ref-idem-dup")
                    .status(PaymentStatus.COMPLETED)
                    .build();

            when(refundRepository.existsByIdempotencyKey("ref-idem-dup")).thenReturn(true);
            when(refundRepository.findByIdempotencyKey("ref-idem-dup")).thenReturn(Optional.of(existing));

            Refund result = paymentService.refundPayment(input);

            assertThat(result.getIdempotencyKey()).isEqualTo("ref-idem-dup");
            verify(paymentRepository, never()).findByIdWithDetails(any());
        }
    }
}
