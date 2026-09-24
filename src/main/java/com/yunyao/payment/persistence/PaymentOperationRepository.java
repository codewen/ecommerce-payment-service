package com.yunyao.payment.persistence;

import com.yunyao.payment.domain.PaymentOperationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperationEntity, UUID> {
    Optional<PaymentOperationEntity> findByCheckoutIdAndType(UUID checkoutId, PaymentOperationType type);
}
