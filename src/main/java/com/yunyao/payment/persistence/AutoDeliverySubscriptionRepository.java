package com.yunyao.payment.persistence;

import com.yunyao.payment.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AutoDeliverySubscriptionRepository extends JpaRepository<AutoDeliverySubscriptionEntity, UUID> {
    List<AutoDeliverySubscriptionEntity> findByStatusAndNextDeliveryOnLessThanEqual(
            SubscriptionStatus status, LocalDate date);
}
