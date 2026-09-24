package com.yunyao.payment.application;

import com.yunyao.payment.domain.PaymentOperationType;
import com.yunyao.payment.domain.PaymentStatus;
import com.yunyao.payment.persistence.CheckoutEntity;
import com.yunyao.payment.persistence.CheckoutRepository;
import com.yunyao.payment.persistence.OutboxEventEntity;
import com.yunyao.payment.persistence.OutboxEventRepository;
import com.yunyao.payment.persistence.PaymentOperationEntity;
import com.yunyao.payment.persistence.PaymentOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Service
public class CheckoutPersistenceService {
    private final CheckoutRepository checkouts;
    private final PaymentOperationRepository operations;
    private final OutboxEventRepository outbox;
    private final Clock clock;

    public CheckoutPersistenceService(CheckoutRepository checkouts,
                                      PaymentOperationRepository operations,
                                      OutboxEventRepository outbox,
                                      Clock clock) {
        this.checkouts = checkouts;
        this.operations = operations;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public CheckoutEntity createOrGet(String idempotencyKey, String requestHash, String sessionId,
                                      CheckoutCommand command, BigDecimal amount, String currency) {
        var existing = checkouts.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException("Idempotency key was reused with a different request");
            }
            return existing.get();
        }
        return checkouts.saveAndFlush(CheckoutEntity.create(idempotencyKey, requestHash, sessionId,
                command.customerId(), command.cartId(), command.cartVersion(), amount, currency, clock.instant()));
    }

    @Transactional
    public PaymentOperationEntity recordOperationIntent(UUID checkoutId, PaymentOperationType type,
                                                        BigDecimal amount, String currency) {
        return operations.findByCheckoutIdAndType(checkoutId, type)
                .orElseGet(() -> operations.saveAndFlush(PaymentOperationEntity.requested(
                        checkoutId, checkoutId + ":" + type.name().toLowerCase(), type,
                        amount, currency, clock.instant())));
    }

    @Transactional
    public CheckoutEntity update(UUID checkoutId, java.util.function.Consumer<CheckoutEntity> update) {
        var checkout = checkouts.findById(checkoutId).orElseThrow();
        update.accept(checkout);
        return checkouts.save(checkout);
    }

    @Transactional
    public void resolveOperation(UUID operationId, PaymentStatus status, String externalId) {
        var operation = operations.findById(operationId).orElseThrow();
        operation.resolve(status, externalId);
    }

    @Transactional
    public CheckoutEntity markPaidAndEnqueue(UUID checkoutId, UUID operationId, String transactionId) {
        var checkout = checkouts.findById(checkoutId).orElseThrow();
        var operation = operations.findById(operationId).orElseThrow();
        operation.resolve(PaymentStatus.CAPTURED, transactionId);
        checkout.paid(transactionId, clock.instant());
        var eventId = "order-paid:" + checkoutId;
        if (!outbox.existsById(eventId)) {
            outbox.save(new OutboxEventEntity(eventId, checkoutId.toString(), "OrderPaid",
                    "{\"checkoutId\":\"%s\",\"orderId\":\"%s\",\"transactionId\":\"%s\"}"
                            .formatted(checkoutId, checkout.getOrderId(), transactionId), clock.instant()));
        }
        return checkout;
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }
}
