package com.yunyao.payment.persistence;

import com.yunyao.payment.domain.OccurrenceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "auto_delivery_occurrence", uniqueConstraints = @UniqueConstraint(
        name = "uq_subscription_scheduled_for", columnNames = {"subscription_id", "scheduled_for"}))
public class AutoDeliveryOccurrenceEntity {
    @Id
    private UUID id;
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;
    @Column(name = "scheduled_for", nullable = false)
    private LocalDate scheduledFor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OccurrenceStatus status;
    @Column(name = "checkout_id")
    private UUID checkoutId;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AutoDeliveryOccurrenceEntity() {}

    public static AutoDeliveryOccurrenceEntity create(UUID subscriptionId, LocalDate scheduledFor, Instant now) {
        var occurrence = new AutoDeliveryOccurrenceEntity();
        occurrence.id = UUID.randomUUID();
        occurrence.subscriptionId = subscriptionId;
        occurrence.scheduledFor = scheduledFor;
        occurrence.status = OccurrenceStatus.CREATED;
        occurrence.createdAt = now;
        return occurrence;
    }

    public UUID getId() { return id; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public LocalDate getScheduledFor() { return scheduledFor; }
    public OccurrenceStatus getStatus() { return status; }
    public UUID getCheckoutId() { return checkoutId; }
    public String getFailureReason() { return failureReason; }
    public void substitutionPending(String reason) { status = OccurrenceStatus.SUBSTITUTION_PENDING; failureReason = reason; }
    public void checkoutStarted(UUID id) { status = OccurrenceStatus.CHECKOUT_STARTED; checkoutId = id; }
    public void completed() { status = OccurrenceStatus.COMPLETED; }
    public void skipped(String reason) { status = OccurrenceStatus.SKIPPED; failureReason = reason; }
    public void failed(String reason) { status = OccurrenceStatus.FAILED; failureReason = reason; }
}
