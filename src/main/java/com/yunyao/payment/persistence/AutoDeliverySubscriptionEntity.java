package com.yunyao.payment.persistence;

import com.yunyao.payment.domain.SubscriptionStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "auto_delivery_subscription")
public class AutoDeliverySubscriptionEntity {
    @Id
    private UUID id;
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    @Column(nullable = false)
    private String postcode;
    @Column(name = "payment_method_token", nullable = false)
    private String paymentMethodToken;
    @Column(name = "cadence_weeks", nullable = false)
    private int cadenceWeeks;
    @Column(name = "next_delivery_on", nullable = false)
    private LocalDate nextDeliveryOn;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;
    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "auto_delivery_item", joinColumns = @JoinColumn(name = "subscription_id"))
    private List<SubscriptionItem> items = new ArrayList<>();

    protected AutoDeliverySubscriptionEntity() {}

    public static AutoDeliverySubscriptionEntity create(String customerId, String postcode,
                                                        String paymentMethodToken, int cadenceWeeks,
                                                        LocalDate nextDeliveryOn,
                                                        List<SubscriptionItem> items, Instant consentedAt) {
        var entity = new AutoDeliverySubscriptionEntity();
        entity.id = UUID.randomUUID();
        entity.customerId = customerId;
        entity.postcode = postcode;
        entity.paymentMethodToken = paymentMethodToken;
        entity.cadenceWeeks = cadenceWeeks;
        entity.nextDeliveryOn = nextDeliveryOn;
        entity.status = SubscriptionStatus.ACTIVE;
        entity.items.addAll(items);
        entity.consentedAt = consentedAt;
        return entity;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getPostcode() { return postcode; }
    public String getPaymentMethodToken() { return paymentMethodToken; }
    public int getCadenceWeeks() { return cadenceWeeks; }
    public LocalDate getNextDeliveryOn() { return nextDeliveryOn; }
    public SubscriptionStatus getStatus() { return status; }
    public List<SubscriptionItem> getItems() { return List.copyOf(items); }
    public void advance() { nextDeliveryOn = nextDeliveryOn.plusWeeks(cadenceWeeks); }
}
