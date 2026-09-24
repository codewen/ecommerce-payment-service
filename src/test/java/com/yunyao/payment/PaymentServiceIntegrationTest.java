package com.yunyao.payment;

import com.yunyao.payment.adapter.braintree.MockBraintreeAdapter;
import com.yunyao.payment.application.AutoDeliveryService;
import com.yunyao.payment.application.CheckoutCommand;
import com.yunyao.payment.application.CheckoutPersistenceService;
import com.yunyao.payment.application.CheckoutService;
import com.yunyao.payment.application.PaymentReconciliationWorker;
import com.yunyao.payment.application.RefundService;
import com.yunyao.payment.domain.CheckoutStatus;
import com.yunyao.payment.domain.Money;
import com.yunyao.payment.persistence.AutoDeliveryOccurrenceRepository;
import com.yunyao.payment.persistence.OutboxEventRepository;
import com.yunyao.payment.port.CommerceToolsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "checkout.reconciliation.fixed-delay=3600000",
        "auto-delivery.discovery-cron=0 0 0 1 1 *"
})
class PaymentServiceIntegrationTest {
    @Autowired CommerceToolsPort commerce;
    @Autowired CheckoutService checkoutService;
    @Autowired PaymentReconciliationWorker reconciliationWorker;
    @Autowired MockBraintreeAdapter braintree;
    @Autowired RefundService refundService;
    @Autowired AutoDeliveryService autoDeliveryService;
    @Autowired AutoDeliveryOccurrenceRepository occurrences;
    @Autowired OutboxEventRepository outbox;

    @BeforeEach
    void stock() {
        commerce.setStock("2000", "DOG-FOOD", 100);
        commerce.setStock("2000", "CAT-LITTER", 100);
    }

    @Test
    void duplicateCheckoutReturnsTheSamePaidCheckoutWithoutAnotherCharge() {
        var cart = cart("customer-1", "DOG-FOOD", "60.00");
        var command = new CheckoutCommand("customer-1", cart.id(), cart.version(), "nonce-success");

        var first = checkoutService.checkout("checkout-key-1", "session-a", command);
        var second = checkoutService.checkout("checkout-key-1", "session-b", command);

        assertThat(first.status()).isEqualTo(CheckoutStatus.PAID);
        assertThat(second.checkoutId()).isEqualTo(first.checkoutId());
        assertThat(second.transactionId()).isEqualTo(first.transactionId());
        assertThat(braintree.invocationCount(first.checkoutId() + ":authorize")).isEqualTo(1);
        assertThat(braintree.invocationCount(first.checkoutId() + ":capture")).isEqualTo(1);
        assertThat(outbox.existsById("order-paid:" + first.checkoutId())).isTrue();
    }

    @Test
    void timeoutIsUnknownUntilReconciliationFindsTheExistingAuthorization() {
        var cart = cart("customer-2", "DOG-FOOD", "30.00");
        var command = new CheckoutCommand("customer-2", cart.id(), cart.version(),
                MockBraintreeAdapter.TIMEOUT_THEN_SUCCEED);

        var pending = checkoutService.checkout("checkout-key-timeout", "session-timeout", command);
        assertThat(pending.status()).isEqualTo(CheckoutStatus.PAYMENT_OUTCOME_UNKNOWN);

        reconciliationWorker.reconcileUnknownAuthorizations();

        var recovered = checkoutService.get(pending.checkoutId());
        assertThat(recovered.status()).isEqualTo(CheckoutStatus.PAID);
        assertThat(braintree.invocationCount(pending.checkoutId() + ":authorize")).isEqualTo(1);
    }

    @Test
    void reusingAnIdempotencyKeyForAnotherCartIsRejected() {
        var firstCart = cart("customer-3", "DOG-FOOD", "10.00");
        var secondCart = cart("customer-3", "CAT-LITTER", "10.00");
        checkoutService.checkout("reused-key", "one", new CheckoutCommand(
                "customer-3", firstCart.id(), firstCart.version(), "nonce-success"));

        assertThatThrownBy(() -> checkoutService.checkout("reused-key", "two", new CheckoutCommand(
                "customer-3", secondCart.id(), secondCart.version(), "nonce-success")))
                .isInstanceOf(CheckoutPersistenceService.IdempotencyConflictException.class);
    }

    @Test
    void partialRefundIsIdempotent() {
        var cart = cart("customer-4", "DOG-FOOD", "50.00");
        var paid = checkoutService.checkout("checkout-refund", "session", new CheckoutCommand(
                "customer-4", cart.id(), cart.version(), "nonce-success"));

        var first = refundService.refund(paid.checkoutId(), "refund-key", new BigDecimal("10.00"), "One item returned");
        var retry = refundService.refund(paid.checkoutId(), "refund-key", new BigDecimal("10.00"), "One item returned");

        assertThat(retry.refundId()).isEqualTo(first.refundId());
        assertThat(retry.externalRefundId()).isEqualTo(first.externalRefundId());
    }

    @Test
    void rerunningDailyDiscoveryDoesNotCreateAnotherOccurrenceOrCharge() {
        var today = LocalDate.now();
        var subscription = autoDeliveryService.create(new AutoDeliveryService.CreateSubscription(
                "customer-5", "2000", "nonce-success", 4, today, true,
                List.of(new AutoDeliveryService.Item("DOG-FOOD", 1, new BigDecimal("25.00")))));

        autoDeliveryService.discoverAndProcess(today);
        autoDeliveryService.discoverAndProcess(today);

        assertThat(occurrences.count()).isEqualTo(1);
        var occurrence = occurrences.findAll().getFirst();
        assertThat(occurrence.getSubscriptionId()).isEqualTo(subscription.subscriptionId());
        assertThat(occurrence.getStatus()).isEqualTo(com.yunyao.payment.domain.OccurrenceStatus.COMPLETED);
    }

    private CommerceToolsPort.CartSnapshot cart(String customer, String sku, String price) {
        return commerce.createReservedCart(customer, "2000",
                List.of(new CommerceToolsPort.CartLine(sku, 1, Money.aud(price))));
    }
}
