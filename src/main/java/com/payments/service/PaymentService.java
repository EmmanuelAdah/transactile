package com.payments.service;

import com.payments.model.dto.PaymentDTOs.*;
import com.payments.model.entity.Payment;
import com.payments.model.entity.Refund;
import com.payments.model.enums.CurrencyCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface PaymentService {

    Payment initiatePayment(InitiatePaymentInput input);

    Payment confirmPayment(UUID paymentId);

    Payment cancelPayment(UUID paymentId, String reason);

    Refund refundPayment(RefundPaymentInput input);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByReferenceId(String referenceId);

    Page<Payment> findAll(PaymentFilterInput filter, PaymentSortInput sort, Pageable pageable);

    PaymentStats getStats(UUID accountId, CurrencyCode currency, String period);
}
