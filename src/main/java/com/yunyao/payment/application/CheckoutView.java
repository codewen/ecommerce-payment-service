package com.yunyao.payment.application;

import com.yunyao.payment.domain.CheckoutStatus;
import com.yunyao.payment.persistence.CheckoutEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutView(
        UUID checkoutId,
        CheckoutStatus status,
        String cartId,
        Long cartVersion,
        String orderId,
        String transactionId,
        BigDecimal amount,
        String currency,
        String message
) {
    public static CheckoutView from(CheckoutEntity entity) {
        var message = switch (entity.getStatus()) {
            case PAYMENT_OUTCOME_UNKNOWN -> "Payment is still being confirmed. Do not submit it again.";
            case OUT_OF_STOCK -> "One or more items are no longer available.";
            case PAYMENT_DECLINED -> "Payment was declined.";
            case PAID -> "Order confirmed and paid.";
            case REQUIRES_REVIEW -> "Payment requires manual review; no new charge should be submitted.";
            default -> entity.getStatus().name();
        };
        return new CheckoutView(entity.getId(), entity.getStatus(), entity.getCartId(),
                entity.getLockedCartVersion(), entity.getOrderId(), entity.getTransactionId(),
                entity.getAmount(), entity.getCurrency(), message);
    }
}
