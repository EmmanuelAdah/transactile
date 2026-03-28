package com.payments.validation;

import com.payments.model.dto.PaymentDTOs.InitiatePaymentInput;
import com.payments.model.entity.Account;
import com.payments.model.enums.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentValidator Unit Tests")
class PaymentValidatorTest {

    @InjectMocks
    private PaymentValidator validator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(validator, "maxTransactionAmount", new BigDecimal("50000"));
    }

    private Account activeAccount(UUID id, CurrencyCode currency, BigDecimal available) {
        return Account.builder()
                .id(id)
                .status(AccountStatus.ACTIVE)
                .currency(currency)
                .balance(available)
                .availableBalance(available)
                .kycVerified(true)
                .createdAt(Instant.now().minusSeconds(86400 * 60))
                .build();
    }

    private InitiatePaymentInput input(UUID senderId, UUID recipientId,
                                       BigDecimal amount, CurrencyCode currency) {
        return new InitiatePaymentInput(senderId, recipientId, amount, currency,
                PaymentMethod.CREDIT_CARD, null, null, UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("should pass for valid payment between two active accounts")
    void shouldPassValidPayment() {
        UUID sid = UUID.randomUUID(), rid = UUID.randomUUID();
        Account sender = activeAccount(sid, CurrencyCode.USD, new BigDecimal("1000.00"));
        Account recipient = activeAccount(rid, CurrencyCode.USD, BigDecimal.ZERO);

        assertThatCode(() -> validator.validatePayment(sender, recipient,
                input(sid, rid, new BigDecimal("100.00"), CurrencyCode.USD)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject payment from suspended sender")
    void shouldRejectSuspendedSender() {
        UUID sid = UUID.randomUUID(), rid = UUID.randomUUID();
        Account sender = Account.builder().id(sid).status(AccountStatus.SUSPENDED).build();
        Account recipient = activeAccount(rid, CurrencyCode.USD, BigDecimal.ZERO);

        assertThatThrownBy(() -> validator.validatePayment(sender, recipient,
                input(sid, rid, BigDecimal.TEN, CurrencyCode.USD)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("should reject self-payment")
    void shouldRejectSelfPayment() {
        UUID id = UUID.randomUUID();
        Account account = activeAccount(id, CurrencyCode.USD, new BigDecimal("1000.00"));

        assertThatThrownBy(() -> validator.validatePayment(account, account,
                input(id, id, BigDecimal.TEN, CurrencyCode.USD)))
                .hasMessageContaining("same account");
    }

    @Test
    @DisplayName("should reject currency mismatch")
    void shouldRejectCurrencyMismatch() {
        UUID sid = UUID.randomUUID(), rid = UUID.randomUUID();
        Account sender = activeAccount(sid, CurrencyCode.USD, new BigDecimal("1000.00"));
        Account recipient = activeAccount(rid, CurrencyCode.EUR, BigDecimal.ZERO);

        assertThatThrownBy(() -> validator.validatePayment(sender, recipient,
                input(sid, rid, new BigDecimal("100.00"), CurrencyCode.EUR)))
                .hasMessageContaining("currency");
    }

    @Test
    @DisplayName("should reject payment exceeding available balance")
    void shouldRejectInsufficientBalance() {
        UUID sid = UUID.randomUUID(), rid = UUID.randomUUID();
        Account sender = activeAccount(sid, CurrencyCode.USD, new BigDecimal("50.00"));
        Account recipient = activeAccount(rid, CurrencyCode.USD, BigDecimal.ZERO);

        assertThatThrownBy(() -> validator.validatePayment(sender, recipient,
                input(sid, rid, new BigDecimal("100.00"), CurrencyCode.USD)))
                .hasMessageContaining("Insufficient");
    }

    @Test
    @DisplayName("should reject payment exceeding max transaction limit")
    void shouldRejectExceedsMaxAmount() {
        UUID sid = UUID.randomUUID(), rid = UUID.randomUUID();
        Account sender = activeAccount(sid, CurrencyCode.USD, new BigDecimal("999999.00"));
        Account recipient = activeAccount(rid, CurrencyCode.USD, BigDecimal.ZERO);

        assertThatThrownBy(() -> validator.validatePayment(sender, recipient,
                input(sid, rid, new BigDecimal("60000.00"), CurrencyCode.USD)))
                .hasMessageContaining("maximum");
    }

    @Test
    @DisplayName("should require KYC for transactions over 10,000")
    void shouldRequireKycForLargeTransactions() {
        UUID sid = UUID.randomUUID(), rid = UUID.randomUUID();
        Account sender = Account.builder()
                .id(sid)
                .status(AccountStatus.ACTIVE)
                .currency(CurrencyCode.USD)
                .balance(new BigDecimal("20000.00"))
                .availableBalance(new BigDecimal("20000.00"))
                .kycVerified(false) // NOT KYC verified
                .createdAt(Instant.now())
                .build();
        Account recipient = activeAccount(rid, CurrencyCode.USD, BigDecimal.ZERO);

        assertThatThrownBy(() -> validator.validatePayment(sender, recipient,
                input(sid, rid, new BigDecimal("15000.00"), CurrencyCode.USD)))
                .hasMessageContaining("KYC");
    }

    @ParameterizedTest
    @DisplayName("should allow large payment when KYC is verified")
    @CsvSource({
            "10001.00, true",
            "49999.00, true"
    })
    void shouldAllowLargePaymentWithKyc(String amount, boolean kycVerified) {
        UUID sid = UUID.randomUUID(), rid = UUID.randomUUID();
        Account sender = Account.builder()
                .id(sid)
                .status(AccountStatus.ACTIVE)
                .currency(CurrencyCode.USD)
                .balance(new BigDecimal("100000.00"))
                .availableBalance(new BigDecimal("100000.00"))
                .kycVerified(kycVerified)
                .createdAt(Instant.now().minusSeconds(86400 * 90))
                .build();
        Account recipient = activeAccount(rid, CurrencyCode.USD, BigDecimal.ZERO);

        assertThatCode(() -> validator.validatePayment(sender, recipient,
                input(sid, rid, new BigDecimal(amount), CurrencyCode.USD)))
                .doesNotThrowAnyException();
    }
}
