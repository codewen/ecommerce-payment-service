package com.yunyao.payment.persistence;

import com.yunyao.payment.domain.CheckoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutRepository extends JpaRepository<CheckoutEntity, UUID> {
    Optional<CheckoutEntity> findByIdempotencyKey(String key);
    List<CheckoutEntity> findByStatus(CheckoutStatus status);
}
