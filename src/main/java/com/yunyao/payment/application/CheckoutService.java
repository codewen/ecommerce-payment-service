package com.yunyao.payment.application;

import com.yunyao.payment.domain.CheckoutStatus;
import com.yunyao.payment.domain.Money;
import com.yunyao.payment.domain.PaymentOperationType;
import com.yunyao.payment.domain.PaymentStatus;
import com.yunyao.payment.persistence.CheckoutEntity;
import com.yunyao.payment.persistence.CheckoutRepository;
import com.yunyao.payment.port.CommerceToolsPort;
import com.yunyao.payment.port.PaymentGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Currency;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class CheckoutService {
    private final CommerceToolsPort commerce;
    private final PaymentGatewayPort payments;
    private final CheckoutPersistenceService persistence;
    private final CheckoutRepository checkouts;
    private final Clock clock;
    private final int reservationMinutes;

    public CheckoutService(CommerceToolsPort commerce, PaymentGatewayPort payments,
                           CheckoutPersistenceService persistence, CheckoutRepository checkouts,
                           Clock clock,
                           @Value("${checkout.reservation-minutes:10}") int reservationMinutes) {
        this.commerce = commerce;
        this.payments = payments;
        this.persistence = persistence;
        this.checkouts = checkouts;
        this.clock = clock;
        this.reservationMinutes = reservationMinutes;
    }

    public CheckoutView checkout(String idempotencyKey, String sessionId, CheckoutCommand command) {
        var initialCart = commerce.getCart(command.cartId());
        var requestHash = sha256(command.customerId() + "|" + command.cartId() + "|" +
                command.cartVersion() + "|" + initialCart.total().amount() + "|" + initialCart.total().currency());
        var checkout = persistence.createOrGet(idempotencyKey, requestHash, sessionId, command,
                initialCart.total().amount(), initialCart.total().currency().getCurrencyCode());

        if (checkout.getStatus() != CheckoutStatus.CREATED) {
            return CheckoutView.from(checkout);
        }

        CommerceToolsPort.CartSnapshot lockedCart;
        try {
            lockedCart = commerce.extendReservations(command.cartId(), command.cartVersion(), reservationMinutes);
        } catch (CommerceToolsPort.OutOfStockException e) {
            return CheckoutView.from(persistence.update(checkout.getId(),
                    entity -> entity.outOfStock(String.join(",", e.skus()), clock.instant())));
        }

        if (!lockedCart.total().amount().equals(checkout.getAmount())) {
            return CheckoutView.from(persistence.update(checkout.getId(), entity -> entity.cancelled(
                    "Cart total changed; customer confirmation is required", clock.instant())));
        }

        checkout = persistence.update(checkout.getId(),
                entity -> entity.lockCart(lockedCart.version(), clock.instant()));
        var operation = persistence.recordOperationIntent(checkout.getId(), PaymentOperationType.AUTHORIZE,
                checkout.getAmount(), checkout.getCurrency());
        var money = new Money(checkout.getAmount(), Currency.getInstance(checkout.getCurrency()));
        var result = payments.authorize(operation.getRequestKey(), command.paymentMethodToken(),
                "checkout-" + checkout.getId(), money);

        if (result.status() == PaymentStatus.OUTCOME_UNKNOWN) {
            persistence.resolveOperation(operation.getId(), PaymentStatus.OUTCOME_UNKNOWN, null);
            return CheckoutView.from(persistence.update(checkout.getId(),
                    entity -> entity.authorizationUnknown(clock.instant())));
        }
        if (result.status() == PaymentStatus.DECLINED) {
            persistence.resolveOperation(operation.getId(), PaymentStatus.DECLINED, result.transactionId());
            return CheckoutView.from(persistence.update(checkout.getId(),
                    entity -> entity.paymentDeclined(result.message(), clock.instant())));
        }

        persistence.resolveOperation(operation.getId(), PaymentStatus.AUTHORIZED, result.transactionId());
        checkout = persistence.update(checkout.getId(),
                entity -> entity.authorized(result.transactionId(), clock.instant()));
        return createOrderAndCapture(checkout);
    }

    public CheckoutView get(UUID checkoutId) {
        return CheckoutView.from(checkouts.findById(checkoutId).orElseThrow());
    }

    CheckoutView createOrderAndCapture(CheckoutEntity checkout) {
        try {
            var order = commerce.createOrder(checkout.getCartId(), checkout.getLockedCartVersion(),
                    "order-" + checkout.getId());
            checkout = persistence.update(checkout.getId(),
                    entity -> entity.orderCreated(order.id(), clock.instant()));
        } catch (RuntimeException error) {
            payments.voidAuthorization(checkout.getId() + ":void", checkout.getAuthorizationId());
            return CheckoutView.from(persistence.update(checkout.getId(),
                    entity -> entity.cancelled("Order creation failed: " + error.getMessage(), clock.instant())));
        }

        checkout = persistence.update(checkout.getId(), entity -> entity.capturePending(clock.instant()));
        var capture = persistence.recordOperationIntent(checkout.getId(), PaymentOperationType.CAPTURE,
                checkout.getAmount(), checkout.getCurrency());
        var result = payments.capture(capture.getRequestKey(), checkout.getAuthorizationId(),
                new Money(checkout.getAmount(), Currency.getInstance(checkout.getCurrency())));
        if (result.status() == PaymentStatus.OUTCOME_UNKNOWN) {
            persistence.resolveOperation(capture.getId(), PaymentStatus.OUTCOME_UNKNOWN, null);
            return CheckoutView.from(persistence.update(checkout.getId(),
                    entity -> entity.authorizationUnknown(clock.instant())));
        }
        return CheckoutView.from(persistence.markPaidAndEnqueue(
                checkout.getId(), capture.getId(), result.transactionId()));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
