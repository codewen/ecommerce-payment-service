package com.yunyao.payment.adapter.commercetools;

import com.yunyao.payment.domain.Money;
import com.yunyao.payment.port.CommerceToolsPort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class InMemoryCommerceToolsAdapter implements CommerceToolsPort {
    private final Clock clock;
    private final Map<String, Integer> inventory = new HashMap<>();
    private final Map<String, MutableCart> carts = new LinkedHashMap<>();
    private final Map<String, OrderSnapshot> ordersByNumber = new HashMap<>();

    public InMemoryCommerceToolsAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized CartSnapshot createReservedCart(String customerId, String postcode, List<CartLine> lines) {
        releaseExpiredReservations();
        ensureAvailable(postcode, lines);
        reserve(postcode, lines);
        var cart = new MutableCart(
                UUID.randomUUID().toString(), 1, customerId, postcode, List.copyOf(lines),
                clock.instant().plus(10, ChronoUnit.MINUTES), false, true
        );
        carts.put(cart.id, cart);
        return cart.snapshot();
    }

    @Override
    public synchronized CartSnapshot getCart(String cartId) {
        releaseExpiredReservations();
        return requireCart(cartId).snapshot();
    }

    @Override
    public synchronized CartSnapshot extendReservations(String cartId, long expectedVersion, int minutes) {
        releaseExpiredReservations();
        var cart = requireCart(cartId);
        requireVersion(cart, expectedVersion);
        if (cart.ordered) {
            throw new CartConflictException("Cart has already been ordered");
        }
        if (!cart.reservationActive) {
            ensureAvailable(cart.postcode, cart.lines);
            reserve(cart.postcode, cart.lines);
            cart.reservationActive = true;
        }
        cart.reservationExpiresAt = clock.instant().plus(minutes, ChronoUnit.MINUTES);
        cart.version++;
        return cart.snapshot();
    }

    @Override
    public synchronized OrderSnapshot createOrder(String cartId, long expectedVersion, String orderNumber) {
        releaseExpiredReservations();
        var previous = ordersByNumber.get(orderNumber);
        if (previous != null) {
            return previous;
        }
        var cart = requireCart(cartId);
        requireVersion(cart, expectedVersion);
        if (!cart.reservationActive) {
            throw new OutOfStockException(cart.lines.stream().map(CartLine::sku).toList());
        }
        cart.ordered = true;
        cart.reservationActive = false;
        cart.version++;
        var order = new OrderSnapshot(UUID.randomUUID().toString(), orderNumber, cart.id, total(cart.lines));
        ordersByNumber.put(orderNumber, order);
        return order;
    }

    @Override
    public synchronized void cancelOrder(String orderId, String reason) {
        ordersByNumber.values().removeIf(order -> order.id().equals(orderId));
    }

    @Override
    public synchronized void setStock(String postcode, String sku, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Stock cannot be negative");
        inventory.put(stockKey(postcode, sku), quantity);
    }

    private void releaseExpiredReservations() {
        var now = clock.instant();
        carts.values().stream()
                .filter(cart -> cart.reservationActive && !cart.ordered && !cart.reservationExpiresAt.isAfter(now))
                .forEach(cart -> {
                    cart.lines.forEach(line -> inventory.merge(
                            stockKey(cart.postcode, line.sku()), line.quantity(), Integer::sum));
                    cart.reservationActive = false;
                });
    }

    private void ensureAvailable(String postcode, List<CartLine> lines) {
        var missing = new ArrayList<String>();
        for (var line : lines) {
            if (inventory.getOrDefault(stockKey(postcode, line.sku()), 0) < line.quantity()) {
                missing.add(line.sku());
            }
        }
        if (!missing.isEmpty()) throw new OutOfStockException(missing);
    }

    private void reserve(String postcode, List<CartLine> lines) {
        lines.forEach(line -> inventory.compute(
                stockKey(postcode, line.sku()),
                (ignored, quantity) -> quantity - line.quantity()));
    }

    private MutableCart requireCart(String id) {
        var cart = carts.get(id);
        if (cart == null) throw new IllegalArgumentException("Unknown cart " + id);
        return cart;
    }

    private void requireVersion(MutableCart cart, long expected) {
        if (cart.version != expected) {
            throw new CartConflictException("Expected cart version %d but found %d".formatted(expected, cart.version));
        }
    }

    private static String stockKey(String postcode, String sku) {
        return postcode + "|" + sku;
    }

    private static Money total(List<CartLine> lines) {
        return lines.stream()
                .map(line -> line.unitPrice().multiply(line.quantity()))
                .reduce(Money.aud("0.00"), Money::add);
    }

    private static final class MutableCart {
        private final String id;
        private long version;
        private final String customerId;
        private final String postcode;
        private final List<CartLine> lines;
        private Instant reservationExpiresAt;
        private boolean ordered;
        private boolean reservationActive;

        private MutableCart(String id, long version, String customerId, String postcode,
                            List<CartLine> lines, Instant reservationExpiresAt,
                            boolean ordered, boolean reservationActive) {
            this.id = id;
            this.version = version;
            this.customerId = customerId;
            this.postcode = postcode;
            this.lines = lines;
            this.reservationExpiresAt = reservationExpiresAt;
            this.ordered = ordered;
            this.reservationActive = reservationActive;
        }

        private CartSnapshot snapshot() {
            return new CartSnapshot(id, version, customerId, postcode, lines, total(lines),
                    reservationExpiresAt, ordered);
        }
    }
}
