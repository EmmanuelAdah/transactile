package com.payments.service.impl;

import com.payments.exception.Exceptions;
import com.payments.model.dto.PaymentDTOs.CreateAccountDTO;
import com.payments.model.entity.Account;
import com.payments.model.entity.User;
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
    public Account createAccount(CreateAccountDTO input) {

//        if (accountRepository.existsByUserIdAndAccountType(user.getId(), type)) {
//            throw new IllegalStateException("Account type already exists for user");
//        }
//
//        if (accountRepository.findByUserId(user.getId()).size() >= 2) {
//            throw new IllegalStateException("User already has maximum allowed accounts");
//        }
//
//        Account account = Account.builder()
//                .user(user)
//                .accountType(type)
//                .email(user.getEmail())
//                .fullName(user.getFullName())
//                .currency(CurrencyCode.USD)
//                .build();

        Account account = Account.builder()
                .currency(input.currency())
                .user(new User())
                .status(AccountStatus.ACTIVE)
                .build();

        account = accountRepository.save(account);
        auditService.log("ACCOUNT_CREATED", "Account", account.getId(), account.getId(), null);
        log.info("Account created: id={}, account number: {}", account.getId(), account.getAccountNumber());
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
    public Optional<Account> findByAccountNumber(Long accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
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
