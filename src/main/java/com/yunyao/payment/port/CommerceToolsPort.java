package com.yunyao.payment.port;

import com.yunyao.payment.domain.Money;

import java.time.Instant;
import java.util.List;

public interface CommerceToolsPort {
    CartSnapshot createReservedCart(String customerId, String postcode, List<CartLine> lines);
    CartSnapshot getCart(String cartId);
    CartSnapshot extendReservations(String cartId, long expectedVersion, int minutes);
    OrderSnapshot createOrder(String cartId, long expectedVersion, String orderNumber);
    void cancelOrder(String orderId, String reason);
    void setStock(String postcode, String sku, int quantity);

    record CartLine(String sku, int quantity, Money unitPrice) {}

    record CartSnapshot(
            String id,
            long version,
            String customerId,
            String postcode,
            List<CartLine> lines,
            Money total,
            Instant reservationExpiresAt,
            boolean ordered
    ) {}

    record OrderSnapshot(String id, String orderNumber, String cartId, Money total) {}

    class CartConflictException extends RuntimeException {
        public CartConflictException(String message) { super(message); }
    }

    class OutOfStockException extends RuntimeException {
        private final List<String> skus;
        public OutOfStockException(List<String> skus) {
            super("Out of stock: " + String.join(", ", skus));
            this.skus = List.copyOf(skus);
        }
        public List<String> skus() { return skus; }
    }
}
