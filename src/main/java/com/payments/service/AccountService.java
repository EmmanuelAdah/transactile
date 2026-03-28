package com.payments.service;

import com.payments.model.dto.PaymentDTOs.CreateAccountInput;
import com.payments.model.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface AccountService {
    Account createAccount(CreateAccountInput input);
    Optional<Account> findById(UUID id);
    Optional<Account> findByEmail(String email);
    Page<Account> findAll(Pageable pageable);
    Account suspendAccount(UUID id, String reason);
    Account reactivateAccount(UUID id);
}
