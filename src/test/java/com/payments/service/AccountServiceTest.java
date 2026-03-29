package com.payments.service;

import com.payments.model.dto.PaymentDTOs.CreateAccountDTO;
import com.payments.model.entity.Account;
import com.payments.model.enums.AccountStatus;
import com.payments.model.enums.CurrencyCode;
import com.payments.repository.AccountRepository;
import com.payments.service.impl.AccountServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AuditService auditService;

    @InjectMocks private AccountServiceImpl accountService;

    @Test
    @DisplayName("should create account when email and externalId are unique")
    void shouldCreateAccount() {
        var input = new CreateAccountDTO("new@example.com", "New User", CurrencyCode.USD, "EXT-NEW");
        Account saved = Account.builder()
                .id(UUID.randomUUID())
                .email(input.email())
                .fullName(input.fullName())
                .status(AccountStatus.ACTIVE)
                .currency(CurrencyCode.USD)
                .externalId(input.externalId())
                .build();

        when(accountRepository.existsByEmail(input.email())).thenReturn(false);
        when(accountRepository.existsByExternalId(input.externalId())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(saved);

        Account result = accountService.createAccount(input);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(auditService).log(eq("ACCOUNT_CREATED"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should throw when email already exists")
    void shouldThrowOnDuplicateEmail() {
        var input = new CreateAccountDTO("dup@example.com", "Dup User", CurrencyCode.USD, "EXT-DUP");
        when(accountRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(input))
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("should throw when externalId already exists")
    void shouldThrowOnDuplicateExternalId() {
        var input = new CreateAccountDTO("unique@example.com", "Unique", CurrencyCode.USD, "EXT-TAKEN");
        when(accountRepository.existsByEmail(any())).thenReturn(false);
        when(accountRepository.existsByExternalId("EXT-TAKEN")).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(input))
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("should suspend an active account")
    void shouldSuspendActiveAccount() {
        UUID id = UUID.randomUUID();
        Account account = Account.builder().id(id).status(AccountStatus.ACTIVE).build();

        when(accountRepository.findById(id)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);

        Account result = accountService.suspendAccount(id, "Fraud");

        assertThat(result.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        verify(auditService).log(eq("ACCOUNT_SUSPENDED"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should throw when suspending already-suspended account")
    void shouldThrowWhenAlreadySuspended() {
        UUID id = UUID.randomUUID();
        Account account = Account.builder().id(id).status(AccountStatus.SUSPENDED).build();

        when(accountRepository.findById(id)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.suspendAccount(id, "reason"))
                .hasMessageContaining("already suspended");
    }

    @Test
    @DisplayName("should reactivate a suspended account")
    void shouldReactivateSuspendedAccount() {
        UUID id = UUID.randomUUID();
        Account account = Account.builder().id(id).status(AccountStatus.SUSPENDED).build();

        when(accountRepository.findById(id)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            return a;
        });

        Account result = accountService.reactivateAccount(id);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("should return empty when account not found by ID")
    void shouldReturnEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        Optional<Account> result = accountService.findById(id);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return paginated accounts")
    void shouldReturnPaginatedAccounts() {
        Account account = Account.builder().id(UUID.randomUUID()).build();
        when(accountRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(account)));

        var page = accountService.findAll(PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
