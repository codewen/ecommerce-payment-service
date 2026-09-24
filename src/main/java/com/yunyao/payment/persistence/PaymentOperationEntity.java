package com.yunyao.payment.persistence;

import com.yunyao.payment.domain.PaymentOperationType;
import com.yunyao.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_operation", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payment_request_key", columnNames = "request_key"),
        @UniqueConstraint(name = "uq_checkout_operation_type", columnNames = {"checkout_id", "operation_type"})
})
public class PaymentOperationEntity {
    @Id
    private UUID id;

    @Column(name = "checkout_id", nullable = false)
    private UUID checkoutId;

    @Column(name = "request_key", nullable = false, updatable = false)
    private String requestKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private PaymentOperationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentOperationEntity() {}

    public static PaymentOperationEntity requested(UUID checkoutId, String key, PaymentOperationType type,
                                                   BigDecimal amount, String currency, Instant now) {
        var entity = new PaymentOperationEntity();
        entity.id = UUID.randomUUID();
        entity.checkoutId = checkoutId;
        entity.requestKey = key;
        entity.type = type;
        entity.status = PaymentStatus.REQUESTED;
        entity.amount = amount;
        entity.currency = currency;
        entity.createdAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getCheckoutId() { return checkoutId; }
    public String getRequestKey() { return requestKey; }
    public PaymentOperationType getType() { return type; }
    public PaymentStatus getStatus() { return status; }
    public String getExternalTransactionId() { return externalTransactionId; }

    public void resolve(PaymentStatus status, String transactionId) {
        this.status = status;
        this.externalTransactionId = transactionId;
    }
}
