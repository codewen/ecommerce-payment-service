package com.yunyao.payment.application;

import com.yunyao.payment.domain.Money;
import com.yunyao.payment.domain.OccurrenceStatus;
import com.yunyao.payment.domain.SubscriptionStatus;
import com.yunyao.payment.persistence.AutoDeliveryOccurrenceEntity;
import com.yunyao.payment.persistence.AutoDeliveryOccurrenceRepository;
import com.yunyao.payment.persistence.AutoDeliverySubscriptionEntity;
import com.yunyao.payment.persistence.AutoDeliverySubscriptionRepository;
import com.yunyao.payment.persistence.OutboxEventEntity;
import com.yunyao.payment.persistence.OutboxEventRepository;
import com.yunyao.payment.persistence.SubscriptionItem;
import com.yunyao.payment.port.CommerceToolsPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AutoDeliveryService {
    private final AutoDeliverySubscriptionRepository subscriptions;
    private final AutoDeliveryOccurrenceRepository occurrences;
    private final OutboxEventRepository outbox;
    private final CommerceToolsPort commerce;
    private final CheckoutService checkoutService;
    private final Clock clock;

    public AutoDeliveryService(AutoDeliverySubscriptionRepository subscriptions,
                               AutoDeliveryOccurrenceRepository occurrences,
                               OutboxEventRepository outbox,
                               CommerceToolsPort commerce,
                               CheckoutService checkoutService,
                               Clock clock) {
        this.subscriptions = subscriptions;
        this.occurrences = occurrences;
        this.outbox = outbox;
        this.commerce = commerce;
        this.checkoutService = checkoutService;
        this.clock = clock;
    }

    public SubscriptionView create(CreateSubscription command) {
        if (!command.futurePaymentConsent()) {
            throw new IllegalArgumentException("Explicit consent is required for future payments");
        }
        if (command.cadenceWeeks() < 1) {
            throw new IllegalArgumentException("Cadence must be at least one week");
        }
        var items = command.items().stream()
                .map(item -> new SubscriptionItem(item.sku(), item.quantity(), item.unitPrice()))
                .toList();
        return SubscriptionView.from(subscriptions.save(AutoDeliverySubscriptionEntity.create(
                command.customerId(), command.postcode(), command.paymentMethodToken(),
                command.cadenceWeeks(), command.nextDeliveryOn(), items, clock.instant())));
    }

    @Scheduled(cron = "${auto-delivery.discovery-cron:0 0 1 * * *}",
            zone = "${auto-delivery.zone:Australia/Sydney}")
    public void dailyDiscovery() {
        discoverAndProcess(LocalDate.now(clock.withZone(ZoneId.of("Australia/Sydney"))));
    }

    public List<OccurrenceView> discoverAndProcess(LocalDate businessDate) {
        var result = new ArrayList<OccurrenceView>();
        for (var subscription : subscriptions.findByStatusAndNextDeliveryOnLessThanEqual(
                SubscriptionStatus.ACTIVE, businessDate)) {
            var occurrence = createIfAbsent(subscription, subscription.getNextDeliveryOn());
            if (occurrence.getStatus() == OccurrenceStatus.CREATED) {
                process(subscription, occurrence, null, false);
            }
            result.add(OccurrenceView.from(occurrences.findById(occurrence.getId()).orElseThrow()));
        }
        return result;
    }

    public OccurrenceView decideSubstitution(UUID occurrenceId, boolean accept,
                                             String replacementSku, BigDecimal replacementPrice) {
        var occurrence = occurrences.findById(occurrenceId).orElseThrow();
        if (occurrence.getStatus() != OccurrenceStatus.SUBSTITUTION_PENDING) {
            throw new IllegalArgumentException("Occurrence is not waiting for substitution consent");
        }
        if (accept && (replacementSku == null || replacementSku.isBlank() || replacementPrice == null
                || replacementPrice.signum() < 0)) {
            throw new IllegalArgumentException(
                    "A replacement SKU and non-negative price are required when accepting a substitution");
        }
        var subscription = subscriptions.findById(occurrence.getSubscriptionId()).orElseThrow();
        process(subscription, occurrence,
                accept ? new Replacement(replacementSku, replacementPrice) : null, true);
        return OccurrenceView.from(occurrences.findById(occurrenceId).orElseThrow());
    }

    private AutoDeliveryOccurrenceEntity createIfAbsent(AutoDeliverySubscriptionEntity subscription,
                                                         LocalDate scheduledFor) {
        var existing = occurrences.findBySubscriptionIdAndScheduledFor(subscription.getId(), scheduledFor);
        if (existing.isPresent()) return existing.get();
        try {
            return occurrences.saveAndFlush(AutoDeliveryOccurrenceEntity.create(
                    subscription.getId(), scheduledFor, clock.instant()));
        } catch (DataIntegrityViolationException duplicate) {
            return occurrences.findBySubscriptionIdAndScheduledFor(subscription.getId(), scheduledFor)
                    .orElseThrow(() -> duplicate);
        }
    }

    private void process(AutoDeliverySubscriptionEntity subscription,
                         AutoDeliveryOccurrenceEntity occurrence,
                         Replacement replacement,
                         boolean substitutionDecisionMade) {
        var missingSku = occurrence.getFailureReason();
        var lines = subscription.getItems().stream()
                .filter(item -> !substitutionDecisionMade || !item.getSku().equals(missingSku))
                .map(item -> new CommerceToolsPort.CartLine(item.getSku(), item.getQuantity(),
                        new Money(item.getLastKnownUnitPrice(), java.util.Currency.getInstance("AUD"))))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (replacement != null) {
            lines.add(new CommerceToolsPort.CartLine(replacement.sku(), 1,
                    new Money(replacement.price(), java.util.Currency.getInstance("AUD"))));
        }
        if (lines.isEmpty()) {
            occurrence.skipped("No available items remain for this delivery");
            occurrences.save(occurrence);
            subscription.advance();
            subscriptions.save(subscription);
            return;
        }

        try {
            var cart = commerce.createReservedCart(subscription.getCustomerId(), subscription.getPostcode(), lines);
            var checkout = checkoutService.checkout(
                    "autodelivery:" + subscription.getId() + ":" + occurrence.getScheduledFor(),
                    "auto-delivery", new CheckoutCommand(subscription.getCustomerId(), cart.id(), cart.version(),
                            subscription.getPaymentMethodToken()));
            occurrence.checkoutStarted(checkout.checkoutId());
            if (checkout.status() == com.yunyao.payment.domain.CheckoutStatus.PAID) {
                occurrence.completed();
                subscription.advance();
                subscriptions.save(subscription);
            }
            occurrences.save(occurrence);
        } catch (CommerceToolsPort.OutOfStockException unavailable) {
            var sku = unavailable.skus().getFirst();
            occurrence.substitutionPending(sku);
            occurrences.save(occurrence);
            var eventId = "substitution-requested:" + occurrence.getId();
            if (!outbox.existsById(eventId)) {
                outbox.save(new OutboxEventEntity(eventId, occurrence.getId().toString(),
                        "SubstitutionRequested",
                        "{\"occurrenceId\":\"%s\",\"unavailableSku\":\"%s\"}"
                                .formatted(occurrence.getId(), sku), clock.instant()));
            }
        } catch (RuntimeException error) {
            occurrence.failed(error.getMessage());
            occurrences.save(occurrence);
        }
    }

    public record CreateSubscription(String customerId, String postcode, String paymentMethodToken,
                                     int cadenceWeeks, LocalDate nextDeliveryOn,
                                     boolean futurePaymentConsent, List<Item> items) {}
    public record Item(String sku, int quantity, BigDecimal unitPrice) {}
    private record Replacement(String sku, BigDecimal price) {}

    public record SubscriptionView(UUID subscriptionId, String customerId, int cadenceWeeks,
                                   LocalDate nextDeliveryOn, SubscriptionStatus status) {
        static SubscriptionView from(AutoDeliverySubscriptionEntity entity) {
            return new SubscriptionView(entity.getId(), entity.getCustomerId(), entity.getCadenceWeeks(),
                    entity.getNextDeliveryOn(), entity.getStatus());
        }
    }

    public record OccurrenceView(UUID occurrenceId, UUID subscriptionId, LocalDate scheduledFor,
                                 OccurrenceStatus status, UUID checkoutId, String detail) {
        static OccurrenceView from(AutoDeliveryOccurrenceEntity entity) {
            return new OccurrenceView(entity.getId(), entity.getSubscriptionId(), entity.getScheduledFor(),
                    entity.getStatus(), entity.getCheckoutId(), entity.getFailureReason());
        }
    }
}
