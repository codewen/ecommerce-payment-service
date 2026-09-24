package com.yunyao.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AutoDeliveryOccurrenceRepository extends JpaRepository<AutoDeliveryOccurrenceEntity, UUID> {
    Optional<AutoDeliveryOccurrenceEntity> findBySubscriptionIdAndScheduledFor(UUID subscriptionId,
                                                                               LocalDate scheduledFor);
}
