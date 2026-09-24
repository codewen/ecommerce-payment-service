package com.yunyao.payment.application;

import com.yunyao.payment.domain.CheckoutStatus;
import com.yunyao.payment.domain.PaymentOperationType;
import com.yunyao.payment.domain.PaymentStatus;
import com.yunyao.payment.persistence.CheckoutRepository;
import com.yunyao.payment.persistence.PaymentOperationRepository;
import com.yunyao.payment.port.PaymentGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class PaymentReconciliationWorker {
    private final CheckoutRepository checkouts;
    private final PaymentOperationRepository operations;
    private final PaymentGatewayPort payments;
    private final CheckoutPersistenceService persistence;
    private final CheckoutService checkoutService;
    private final Clock clock;
    private final int fastAttempts;

    public PaymentReconciliationWorker(CheckoutRepository checkouts,
                                       PaymentOperationRepository operations,
                                       PaymentGatewayPort payments,
                                       CheckoutPersistenceService persistence,
                                       CheckoutService checkoutService,
                                       Clock clock,
                                       @Value("${checkout.reconciliation.fast-attempts:6}") int fastAttempts) {
        this.checkouts = checkouts;
        this.operations = operations;
        this.payments = payments;
        this.persistence = persistence;
        this.checkoutService = checkoutService;
        this.clock = clock;
        this.fastAttempts = fastAttempts;
    }

    @Scheduled(fixedDelayString = "${checkout.reconciliation.fixed-delay:30000}")
    public void reconcileUnknownAuthorizations() {
        for (var checkout : checkouts.findByStatus(CheckoutStatus.PAYMENT_OUTCOME_UNKNOWN)) {
            var operation = operations.findByCheckoutIdAndType(checkout.getId(), PaymentOperationType.AUTHORIZE)
                    .orElseThrow();
            var result = payments.findByRequestKey(operation.getRequestKey());
            persistence.update(checkout.getId(), entity -> entity.reconciliationAttempt(clock.instant()));
            if (result.status() == PaymentStatus.AUTHORIZED) {
                persistence.resolveOperation(operation.getId(), PaymentStatus.AUTHORIZED, result.transactionId());
                var authorized = persistence.update(checkout.getId(),
                        entity -> entity.authorized(result.transactionId(), clock.instant()));
                checkoutService.createOrderAndCapture(authorized);
            } else if (result.status() == PaymentStatus.DECLINED || result.status() == PaymentStatus.FAILED) {
                persistence.resolveOperation(operation.getId(), result.status(), result.transactionId());
                persistence.update(checkout.getId(),
                        entity -> entity.paymentDeclined(result.message(), clock.instant()));
            } else if (checkout.getReconciliationAttempts() + 1 >= fastAttempts) {
                persistence.update(checkout.getId(), entity -> entity.requiresReview(
                        "Fast reconciliation window exhausted; payment outcome remains unknown", clock.instant()));
            }
        }
    }
}
