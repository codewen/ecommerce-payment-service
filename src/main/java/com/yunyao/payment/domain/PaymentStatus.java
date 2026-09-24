package com.yunyao.payment.domain;

public enum PaymentStatus {
    REQUESTED,
    OUTCOME_UNKNOWN,
    AUTHORIZED,
    CAPTURED,
    DECLINED,
    VOIDED,
    REFUNDED,
    FAILED
}
