package com.payments.model.entity;

import com.payments.model.enums.CurrencyCode;
import com.payments.model.enums.PaymentMethod;
import com.payments.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_reference_id", columnList = "reference_id", unique = true),
        @Index(name = "idx_payments_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_payments_sender", columnList = "sender_id"),
        @Index(name = "idx_payments_recipient", columnList = "recipient_id"),
        @Index(name = "idx_payments_status", columnList = "status"),
        @Index(name = "idx_payments_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"sender", "recipient", "transactions", "refunds"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "reference_id", nullable = false, unique = true, length = 50)
    private String referenceId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Column(length = 500)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "processing_fee", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal processingFee = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Account sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Account recipient;

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Refund> refunds = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Domain methods
    public void transitionTo(PaymentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("Cannot transition payment from %s to %s", this.status, newStatus));
        }
        this.status = newStatus;
        if (newStatus == PaymentStatus.COMPLETED) {
            this.completedAt = Instant.now();
        }
    }

    public BigDecimal getRefundedAmount() {
        return refunds.stream()
                .filter(r -> r.getStatus() == PaymentStatus.COMPLETED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isFullyRefunded() {
        return getRefundedAmount().compareTo(amount) >= 0;
    }

    public boolean canBeRefunded() {
        return status == PaymentStatus.COMPLETED &&
                getRefundedAmount().compareTo(amount) < 0;
    }
}
