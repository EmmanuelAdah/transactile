package com.payments.graphql;

import com.payments.model.dto.PaymentDTOs.*;
import com.payments.model.entity.Account;
import com.payments.model.entity.Payment;
import com.payments.model.enums.*;
import com.payments.service.AccountService;
import com.payments.service.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@GraphQlTest
@DisplayName("GraphQL Controller Integration Tests")
@WithMockUser(roles = "USER")
class PaymentGraphQLControllerTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockBean private PaymentService paymentService;
    @MockBean private AccountService accountService;

    private Account testAccount;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .id(UUID.fromString("a0000000-0000-0000-0000-000000000001"))
                .externalId("EXT-001")
                .email("alice@example.com")
                .fullName("Alice Johnson")
                .status(AccountStatus.ACTIVE)
                .currency(CurrencyCode.USD)
                .balance(new BigDecimal("5000.00"))
                .availableBalance(new BigDecimal("5000.00"))
                .kycVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Account recipient = Account.builder()
                .id(UUID.fromString("a0000000-0000-0000-0000-000000000002"))
                .email("bob@example.com")
                .fullName("Bob Smith")
                .status(AccountStatus.ACTIVE)
                .currency(CurrencyCode.USD)
                .build();

        testPayment = Payment.builder()
                .id(UUID.randomUUID())
                .referenceId("PAY-TEST-001")
                .amount(new BigDecimal("100.00"))
                .currency(CurrencyCode.USD)
                .status(PaymentStatus.COMPLETED)
                .method(PaymentMethod.CREDIT_CARD)
                .idempotencyKey("test-idem-001")
                .processingFee(new BigDecimal("3.20"))
                .netAmount(new BigDecimal("96.80"))
                .sender(testAccount)
                .recipient(recipient)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ─── Account Query Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("should fetch account by ID via GraphQL query")
    void shouldFetchAccountById() {
        when(accountService.findById(testAccount.getId()))
                .thenReturn(Optional.of(testAccount));

        graphQlTester.document("""
                query GetAccount($id: UUID!) {
                    account(id: $id) {
                        id
                        email
                        fullName
                        status
                        currency
                        balance
                        kycVerified
                    }
                }
                """)
                .variable("id", testAccount.getId().toString())
                .execute()
                .path("account.email").entity(String.class).isEqualTo("alice@example.com")
                .path("account.fullName").entity(String.class).isEqualTo("Alice Johnson")
                .path("account.status").entity(String.class).isEqualTo("ACTIVE")
                .path("account.kycVerified").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    @DisplayName("should return error when account not found")
    void shouldReturnErrorWhenAccountNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(accountService.findById(unknownId)).thenReturn(Optional.empty());

        graphQlTester.document("""
                query {
                    account(id: "%s") {
                        id
                        email
                    }
                }
                """.formatted(unknownId))
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).isNotEmpty();
                    assertThat(errors.get(0).getMessage()).contains("not found");
                });
    }

    @Test
    @DisplayName("should fetch account by email")
    void shouldFetchAccountByEmail() {
        when(accountService.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(testAccount));

        graphQlTester.document("""
                query {
                    accountByEmail(email: "alice@example.com") {
                        email
                        currency
                    }
                }
                """)
                .execute()
                .path("accountByEmail.email").entity(String.class).isEqualTo("alice@example.com")
                .path("accountByEmail.currency").entity(String.class).isEqualTo("USD");
    }

    // ─── Payment Query Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("should fetch payment by ID with all fields")
    void shouldFetchPaymentById() {
        when(paymentService.findById(testPayment.getId()))
                .thenReturn(Optional.of(testPayment));

        graphQlTester.document("""
                query GetPayment($id: UUID!) {
                    payment(id: $id) {
                        id
                        referenceId
                        amount
                        currency
                        status
                        method
                        processingFee
                        netAmount
                    }
                }
                """)
                .variable("id", testPayment.getId().toString())
                .execute()
                .path("payment.referenceId").entity(String.class).isEqualTo("PAY-TEST-001")
                .path("payment.status").entity(String.class).isEqualTo("COMPLETED")
                .path("payment.method").entity(String.class).isEqualTo("CREDIT_CARD");
    }

    @Test
    @DisplayName("should fetch payment by reference ID")
    void shouldFetchPaymentByReferenceId() {
        when(paymentService.findByReferenceId("PAY-TEST-001"))
                .thenReturn(Optional.of(testPayment));

        graphQlTester.document("""
                query {
                    paymentByReference(referenceId: "PAY-TEST-001") {
                        referenceId
                        amount
                        status
                    }
                }
                """)
                .execute()
                .path("paymentByReference.referenceId").entity(String.class).isEqualTo("PAY-TEST-001");
    }

    @Test
    @DisplayName("should list payments with pagination")
    void shouldListPaymentsWithPagination() {
        when(paymentService.findAll(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testPayment)));

        graphQlTester.document("""
                query {
                    payments(page: 0, size: 10) {
                        content {
                            id
                            referenceId
                            amount
                            status
                        }
                        totalElements
                        hasNext
                    }
                }
                """)
                .execute()
                .path("payments.content").entityList(Object.class).hasSize(1)
                .path("payments.totalElements").entity(Integer.class).isEqualTo(1)
                .path("payments.hasNext").entity(Boolean.class).isEqualTo(false);
    }

    // ─── Mutation Tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("should create account via mutation")
    void shouldCreateAccount() {
        when(accountService.createAccount(any(CreateAccountDTO.class)))
                .thenReturn(testAccount);

        graphQlTester.document("""
                mutation CreateAccount($input: CreateAccountInput!) {
                    createAccount(input: $input) {
                        email
                        fullName
                        status
                        currency
                    }
                }
                """)
                .variable("input", java.util.Map.of(
                        "email", "alice@example.com",
                        "fullName", "Alice Johnson",
                        "currency", "USD",
                        "externalId", "EXT-001"
                ))
                .execute()
                .path("createAccount.email").entity(String.class).isEqualTo("alice@example.com")
                .path("createAccount.status").entity(String.class).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("should initiate payment mutation and return success result")
    void shouldInitiatePaymentMutation() {
        when(paymentService.initiatePayment(any(InitiatePaymentDTO.class)))
                .thenReturn(testPayment);

        graphQlTester.document("""
                mutation InitiatePayment($input: InitiatePaymentInput!) {
                    initiatePayment(input: $input) {
                        success
                        message
                        payment {
                            referenceId
                            status
                            amount
                        }
                    }
                }
                """)
                .variable("input", java.util.Map.of(
                        "senderId", testAccount.getId().toString(),
                        "recipientId", UUID.randomUUID().toString(),
                        "amount", "100.00",
                        "currency", "USD",
                        "method", "CREDIT_CARD",
                        "idempotencyKey", "test-idem-001"
                ))
                .execute()
                .path("initiatePayment.success").entity(Boolean.class).isEqualTo(true)
                .path("initiatePayment.payment.referenceId").entity(String.class).isEqualTo("PAY-TEST-001");
    }

    @Test
    @DisplayName("should return failure result when service throws")
    void shouldReturnFailureWhenServiceThrows() {
        when(paymentService.initiatePayment(any()))
                .thenThrow(new IllegalStateException("Insufficient funds"));

        graphQlTester.document("""
                mutation InitiatePayment($input: InitiatePaymentInput!) {
                    initiatePayment(input: $input) {
                        success
                        message
                    }
                }
                """)
                .variable("input", java.util.Map.of(
                        "senderId", UUID.randomUUID().toString(),
                        "recipientId", UUID.randomUUID().toString(),
                        "amount", "9999.00",
                        "currency", "USD",
                        "method", "CREDIT_CARD",
                        "idempotencyKey", "test-idem-fail"
                ))
                .execute()
                .path("initiatePayment.success").entity(Boolean.class).isEqualTo(false)
                .path("initiatePayment.message").entity(String.class).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("should suspend account via mutation")
    void shouldSuspendAccount() {
        Account suspended = Account.builder()
                .id(testAccount.getId())
                .email(testAccount.getEmail())
                .status(AccountStatus.SUSPENDED)
                .currency(CurrencyCode.USD)
                .build();

        when(accountService.suspendAccount(testAccount.getId(), "Policy violation"))
                .thenReturn(suspended);

        graphQlTester.document("""
                mutation SuspendAccount($id: UUID!, $reason: String!) {
                    suspendAccount(id: $id, reason: $reason) {
                        id
                        status
                    }
                }
                """)
                .variable("id", testAccount.getId().toString())
                .variable("reason", "Policy violation")
                .execute()
                .path("suspendAccount.status").entity(String.class).isEqualTo("SUSPENDED");
    }
}
