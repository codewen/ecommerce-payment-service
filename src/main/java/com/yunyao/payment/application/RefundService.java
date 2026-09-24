package com.yunyao.payment.application;

import com.yunyao.payment.domain.CheckoutStatus;
import com.yunyao.payment.domain.Money;
import com.yunyao.payment.domain.PaymentStatus;
import com.yunyao.payment.domain.RefundStatus;
import com.yunyao.payment.persistence.CheckoutRepository;
import com.yunyao.payment.persistence.RefundEntity;
import com.yunyao.payment.persistence.RefundRepository;
import com.yunyao.payment.port.PaymentGatewayPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Currency;
import java.util.UUID;

@Service
public class RefundService {
    private final CheckoutRepository checkouts;
    private final RefundRepository refunds;
    private final PaymentGatewayPort payments;
    private final Clock clock;

    public RefundService(CheckoutRepository checkouts, RefundRepository refunds,
                         PaymentGatewayPort payments, Clock clock) {
        this.checkouts = checkouts;
        this.refunds = refunds;
        this.payments = payments;
        this.clock = clock;
    }

    public RefundView refund(UUID checkoutId, String idempotencyKey, BigDecimal amount, String reason) {
        var checkout = checkouts.findById(checkoutId).orElseThrow();
        if (checkout.getStatus() != CheckoutStatus.PAID) {
            throw new IllegalArgumentException("Only paid checkouts can be refunded");
        }
        var existing = refunds.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (existing.get().getCheckoutId().equals(checkoutId) &&
                    existing.get().getAmount().compareTo(amount) == 0) {
                return RefundView.from(existing.get());
            }
            throw new CheckoutPersistenceService.IdempotencyConflictException(
                    "Refund idempotency key was reused with different input");
        }
        var alreadyRefunded = refunds.sumAmountByCheckoutIdAndStatus(checkoutId, RefundStatus.REFUNDED);
        if (amount.signum() <= 0 || alreadyRefunded.add(amount).compareTo(checkout.getAmount()) > 0) {
            throw new IllegalArgumentException("Refund exceeds the remaining refundable amount");
        }
        var refund = createIntent(checkoutId, idempotencyKey, amount, reason);
        var result = payments.refund(refund.getRequestKey(), checkout.getTransactionId(),
                new Money(amount, Currency.getInstance(checkout.getCurrency())));
        if (result.status() != PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Refund result was not terminal: " + result.status());
        }
        return complete(refund.getId(), result.transactionId());
    }

    @Transactional
    protected RefundEntity createIntent(UUID checkoutId, String key, BigDecimal amount, String reason) {
        return refunds.saveAndFlush(RefundEntity.requested(checkoutId, key, amount, reason, clock.instant()));
    }

    @Transactional
    protected RefundView complete(UUID refundId, String externalId) {
        var refund = refunds.findById(refundId).orElseThrow();
        refund.refunded(externalId);
        return RefundView.from(refunds.save(refund));
    }

    public record RefundView(UUID refundId, UUID checkoutId, BigDecimal amount,
                             RefundStatus status, String externalRefundId) {
        static RefundView from(RefundEntity entity) {
            return new RefundView(entity.getId(), entity.getCheckoutId(), entity.getAmount(),
                    entity.getStatus(), entity.getExternalRefundId());
        }
    }
}
