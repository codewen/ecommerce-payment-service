package com.yunyao.payment.persistence;

import com.yunyao.payment.domain.CheckoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "uq_checkout_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uq_checkout_cart_version", columnNames = {"cart_id", "requested_cart_version"})
})
public class CheckoutEntity {
    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "cart_id", nullable = false)
    private String cartId;

    @Column(name = "requested_cart_version", nullable = false)
    private long requestedCartVersion;

    @Column(name = "locked_cart_version")
    private Long lockedCartVersion;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckoutStatus status;

    @Column(name = "authorization_id")
    private String authorizationId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "reconciliation_attempts", nullable = false)
    private int reconciliationAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long rowVersion;

    protected CheckoutEntity() {}

    public static CheckoutEntity create(String idempotencyKey, String requestHash, String sessionId,
                                        String customerId, String cartId, long cartVersion,
                                        BigDecimal amount, String currency, Instant now) {
        var entity = new CheckoutEntity();
        entity.id = UUID.randomUUID();
        entity.idempotencyKey = idempotencyKey;
        entity.requestHash = requestHash;
        entity.sessionId = sessionId;
        entity.customerId = customerId;
        entity.cartId = cartId;
        entity.requestedCartVersion = cartVersion;
        entity.amount = amount;
        entity.currency = currency;
        entity.status = CheckoutStatus.CREATED;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getCustomerId() { return customerId; }
    public String getCartId() { return cartId; }
    public long getRequestedCartVersion() { return requestedCartVersion; }
    public Long getLockedCartVersion() { return lockedCartVersion; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public CheckoutStatus getStatus() { return status; }
    public String getAuthorizationId() { return authorizationId; }
    public String getTransactionId() { return transactionId; }
    public String getOrderId() { return orderId; }
    public String getFailureReason() { return failureReason; }
    public int getReconciliationAttempts() { return reconciliationAttempts; }

    public void lockCart(long version, Instant now) {
        lockedCartVersion = version;
        transition(CheckoutStatus.PAYMENT_AUTHORIZATION_PENDING, now);
    }

    public void authorizationUnknown(Instant now) {
        transition(CheckoutStatus.PAYMENT_OUTCOME_UNKNOWN, now);
    }

    public void authorized(String id, Instant now) {
        authorizationId = id;
        transition(CheckoutStatus.PAYMENT_AUTHORIZED, now);
    }

    public void paymentDeclined(String reason, Instant now) {
        failureReason = reason;
        transition(CheckoutStatus.PAYMENT_DECLINED, now);
    }

    public void orderCreated(String id, Instant now) {
        orderId = id;
        transition(CheckoutStatus.ORDER_CREATED, now);
    }

    public void capturePending(Instant now) {
        transition(CheckoutStatus.CAPTURE_PENDING, now);
    }

    public void paid(String id, Instant now) {
        transactionId = id;
        transition(CheckoutStatus.PAID, now);
    }

    public void outOfStock(String reason, Instant now) {
        failureReason = reason;
        transition(CheckoutStatus.OUT_OF_STOCK, now);
    }

    public void cancelled(String reason, Instant now) {
        failureReason = reason;
        transition(CheckoutStatus.CANCELLED, now);
    }

    public void reconciliationAttempt(Instant now) {
        reconciliationAttempts++;
        updatedAt = now;
    }

    public void requiresReview(String reason, Instant now) {
        failureReason = reason;
        transition(CheckoutStatus.REQUIRES_REVIEW, now);
    }

    private void transition(CheckoutStatus next, Instant now) {
        status = next;
        updatedAt = now;
    }
}
