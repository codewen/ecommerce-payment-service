package com.yunyao.payment.application;

public record CheckoutCommand(
        String customerId,
        String cartId,
        long cartVersion,
        String paymentMethodToken
) {}
