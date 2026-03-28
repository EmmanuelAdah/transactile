package com.payments.repository;

import com.payments.model.entity.Payment;
import com.payments.model.enums.CurrencyCode;
import com.payments.model.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByReferenceId(String referenceId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT p FROM Payment p WHERE p.sender.id = :accountId OR p.recipient.id = :accountId ORDER BY p.createdAt DESC")
    Page<Payment> findByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    @Query("""
            SELECT p FROM Payment p
            WHERE (p.sender.id = :accountId OR p.recipient.id = :accountId)
            AND p.status = :status
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> findByAccountIdAndStatus(
            @Param("accountId") UUID accountId,
            @Param("status") PaymentStatus status,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.sender.id = :accountId
            AND p.currency = :currency
            AND p.status IN ('COMPLETED', 'PROCESSING')
            AND p.createdAt >= :since
            """)
    BigDecimal sumAmountBySenderSince(
            @Param("accountId") UUID accountId,
            @Param("currency") CurrencyCode currency,
            @Param("since") Instant since);

    @Query("""
            SELECT COUNT(p), SUM(p.amount), AVG(p.amount),
                   SUM(CASE WHEN p.status = 'COMPLETED' THEN 1 ELSE 0 END)
            FROM Payment p
            WHERE (:accountId IS NULL OR p.sender.id = :accountId OR p.recipient.id = :accountId)
            AND p.currency = :currency
            AND p.createdAt >= :since
            """)
    Object[] getPaymentStats(
            @Param("accountId") UUID accountId,
            @Param("currency") CurrencyCode currency,
            @Param("since") Instant since);

    @EntityGraph(attributePaths = {"sender", "recipient", "transactions", "refunds"})
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithDetails(@Param("id") UUID id);
}
