package com.yunyao.payment.adapter.braintree;

import com.yunyao.payment.domain.Money;
import com.yunyao.payment.domain.PaymentStatus;
import com.yunyao.payment.port.PaymentGatewayPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class MockBraintreeAdapter implements PaymentGatewayPort {
    public static final String TIMEOUT_THEN_SUCCEED = "nonce-timeout-success";
    public static final String DECLINE = "nonce-decline";

    private final Map<String, GatewayResult> operations = new HashMap<>();
    private final Map<String, Integer> invocationCounts = new HashMap<>();

    @Override
    public synchronized GatewayResult authorize(String requestKey, String paymentMethodToken,
                                                String merchantOrderId, Money amount) {
        var existing = operations.get(requestKey);
        if (existing != null) return existing;
        invocationCounts.merge(requestKey, 1, Integer::sum);
        if (DECLINE.equals(paymentMethodToken)) {
            return remember(requestKey, new GatewayResult(null, PaymentStatus.DECLINED, "Processor declined"));
        }
        var result = new GatewayResult("auth-" + UUID.randomUUID(), PaymentStatus.AUTHORIZED, "Authorized");
        operations.put(requestKey, result);
        if (TIMEOUT_THEN_SUCCEED.equals(paymentMethodToken)) {
            return GatewayResult.unknown("Gateway timed out; final state is unknown");
        }
        return result;
    }

    @Override
    public synchronized GatewayResult capture(String requestKey, String authorizationId, Money amount) {
        var existing = operations.get(requestKey);
        if (existing != null) return existing;
        invocationCounts.merge(requestKey, 1, Integer::sum);
        return remember(requestKey,
                new GatewayResult("txn-" + UUID.randomUUID(), PaymentStatus.CAPTURED, "Captured"));
    }

    @Override
    public synchronized GatewayResult voidAuthorization(String requestKey, String authorizationId) {
        return operations.computeIfAbsent(requestKey,
                ignored -> new GatewayResult(authorizationId, PaymentStatus.VOIDED, "Authorization voided"));
    }

    @Override
    public synchronized GatewayResult refund(String requestKey, String transactionId, Money amount) {
        return operations.computeIfAbsent(requestKey,
                ignored -> new GatewayResult("refund-" + UUID.randomUUID(), PaymentStatus.REFUNDED, "Refunded"));
    }

    @Override
    public synchronized GatewayResult findByRequestKey(String requestKey) {
        return operations.getOrDefault(requestKey, GatewayResult.unknown("No terminal result yet"));
    }

    public synchronized int invocationCount(String requestKey) {
        return invocationCounts.getOrDefault(requestKey, 0);
    }

    private GatewayResult remember(String key, GatewayResult result) {
        operations.put(key, result);
        return result;
    }
}
