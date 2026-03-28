package com.payments.graphql.resolver;

import com.payments.model.entity.Account;
import com.payments.model.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time subscription controller.
 * Uses Project Reactor Sinks to multicast events to subscribers.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    // Sinks per payment ID — clients subscribe to specific payment updates
    private final Map<UUID, Sinks.Many<Payment>> paymentSinks = new ConcurrentHashMap<>();
    private final Map<UUID, Sinks.Many<Account>> accountSinks = new ConcurrentHashMap<>();

    @SubscriptionMapping
    public Publisher<Payment> paymentStatusUpdated(@Argument UUID paymentId) {
        log.debug("New subscriber for payment: {}", paymentId);
        Sinks.Many<Payment> sink = paymentSinks.computeIfAbsent(
                paymentId,
                id -> Sinks.many().multicast().onBackpressureBuffer()
        );
        return sink.asFlux()
                .doOnCancel(() -> {
                    log.debug("Subscriber disconnected from payment: {}", paymentId);
                    paymentSinks.remove(paymentId);
                });
    }

    @SubscriptionMapping
    public Publisher<Account> accountBalanceUpdated(@Argument UUID accountId) {
        log.debug("New subscriber for account balance: {}", accountId);
        Sinks.Many<Account> sink = accountSinks.computeIfAbsent(
                accountId,
                id -> Sinks.many().multicast().onBackpressureBuffer()
        );
        return sink.asFlux()
                .doOnCancel(() -> accountSinks.remove(accountId));
    }

    // ─── Event Publishers (called by service layer) ───────────────────────────

    public void publishPaymentUpdate(Payment payment) {
        Sinks.Many<Payment> sink = paymentSinks.get(payment.getId());
        if (sink != null) {
            sink.tryEmitNext(payment);
        }
    }

    public void publishAccountBalanceUpdate(Account account) {
        Sinks.Many<Account> sink = accountSinks.get(account.getId());
        if (sink != null) {
            sink.tryEmitNext(account);
        }
    }
}
