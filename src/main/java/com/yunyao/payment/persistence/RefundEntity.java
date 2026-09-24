package com.yunyao.payment.persistence;

import com.yunyao.payment.domain.RefundStatus;
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
@Table(name = "refund", uniqueConstraints = @UniqueConstraint(
        name = "uq_refund_idempotency", columnNames = "idempotency_key"))
public class RefundEntity {
    @Id
    private UUID id;
    @Column(name = "checkout_id", nullable = false)
    private UUID checkoutId;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "request_key", nullable = false, unique = true)
    private String requestKey;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;
    @Column(name = "external_refund_id")
    private String externalRefundId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefundEntity() {}

    public static RefundEntity requested(UUID checkoutId, String idempotencyKey,
                                         BigDecimal amount, String reason, Instant now) {
        var refund = new RefundEntity();
        refund.id = UUID.randomUUID();
        refund.checkoutId = checkoutId;
        refund.idempotencyKey = idempotencyKey;
        refund.requestKey = refund.id + ":refund";
        refund.amount = amount;
        refund.reason = reason;
        refund.status = RefundStatus.REQUESTED;
        refund.createdAt = now;
        return refund;
    }

    public UUID getId() { return id; }
    public UUID getCheckoutId() { return checkoutId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestKey() { return requestKey; }
    public BigDecimal getAmount() { return amount; }
    public RefundStatus getStatus() { return status; }
    public String getExternalRefundId() { return externalRefundId; }

    public void refunded(String externalId) {
        status = RefundStatus.REFUNDED;
        externalRefundId = externalId;
    }
}
