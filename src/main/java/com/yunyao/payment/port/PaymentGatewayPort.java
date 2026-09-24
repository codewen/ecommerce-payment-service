package com.yunyao.payment.port;

import com.yunyao.payment.domain.Money;
import com.yunyao.payment.domain.PaymentStatus;

public interface PaymentGatewayPort {
    GatewayResult authorize(String requestKey, String paymentMethodToken, String merchantOrderId, Money amount);
    GatewayResult capture(String requestKey, String authorizationId, Money amount);
    GatewayResult voidAuthorization(String requestKey, String authorizationId);
    GatewayResult refund(String requestKey, String transactionId, Money amount);
    GatewayResult findByRequestKey(String requestKey);

    record GatewayResult(String transactionId, PaymentStatus status, String message) {
        public static GatewayResult unknown(String message) {
            return new GatewayResult(null, PaymentStatus.OUTCOME_UNKNOWN, message);
        }
    }
}
