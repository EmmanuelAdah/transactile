package com.payments.service.impl;

import com.payments.exception.Exceptions;
import com.payments.model.dto.PaymentDTOs.CreateAccountInput;
import com.payments.model.entity.Account;
import com.payments.model.enums.AccountStatus;
import com.payments.repository.AccountRepository;
import com.payments.service.AccountService;
import com.payments.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public Account createAccount(CreateAccountInput input) {
        if (accountRepository.existsByEmail(input.email())) {
            throw Exceptions.duplicate("An account with email " + input.email() + " already exists");
        }
        if (accountRepository.existsByExternalId(input.externalId())) {
            throw Exceptions.duplicate("An account with externalId " + input.externalId() + " already exists");
        }

        Account account = Account.builder()
                .email(input.email())
                .fullName(input.fullName())
                .currency(input.currency())
                .userId(input.externalId())
                .status(AccountStatus.ACTIVE)
                .build();

        account = accountRepository.save(account);
        auditService.log("ACCOUNT_CREATED", "Account", account.getId(), account.getId(), null);
        log.info("Account created: id={}, email={}", account.getId(), account.getEmail());
        return account;
    }

    @Override
    @Cacheable(value = "accounts", key = "#id")
    @Transactional(readOnly = true)
    public Optional<Account> findById(UUID id) {
        return accountRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Account> findAll(Pageable pageable) {
        return accountRepository.findAll(pageable);
    }

    @Override
    @Transactional
    @CacheEvict(value = "accounts", key = "#id")
    public Account suspendAccount(UUID id, String reason) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> Exceptions.notFound("Account", id));

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw Exceptions.invalidState("Account is already suspended");
        }

        account.setStatus(AccountStatus.SUSPENDED);
        account = accountRepository.save(account);
        auditService.log("ACCOUNT_SUSPENDED", "Account", id, id, "reason=" + reason);
        return account;
    }

    @Override
    @Transactional
    @CacheEvict(value = "accounts", key = "#id")
    public Account reactivateAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> Exceptions.notFound("Account", id));

        if (account.getStatus() != AccountStatus.SUSPENDED) {
            throw Exceptions.invalidState("Account is not suspended");
        }

        account.setStatus(AccountStatus.ACTIVE);
        account = accountRepository.save(account);
        auditService.log("ACCOUNT_REACTIVATED", "Account", id, id, null);
        return account;
    }
}
