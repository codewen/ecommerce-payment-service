package com.yunyao.payment.web;

import com.yunyao.payment.application.AutoDeliveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auto-deliveries")
public class AutoDeliveryController {
    private final AutoDeliveryService service;

    public AutoDeliveryController(AutoDeliveryService service) {
        this.service = service;
    }

    @PostMapping
    AutoDeliveryService.SubscriptionView create(@Valid @RequestBody CreateRequest request) {
        return service.create(new AutoDeliveryService.CreateSubscription(
                request.customerId(), request.postcode(), request.paymentMethodToken(),
                request.cadenceWeeks(), request.nextDeliveryOn(), request.futurePaymentConsent(),
                request.items().stream()
                        .map(item -> new AutoDeliveryService.Item(item.sku(), item.quantity(), item.unitPrice()))
                        .toList()));
    }

    @PostMapping("/admin/run/{businessDate}")
    List<AutoDeliveryService.OccurrenceView> run(@PathVariable LocalDate businessDate) {
        return service.discoverAndProcess(businessDate);
    }

    @PostMapping("/occurrences/{occurrenceId}/substitution")
    AutoDeliveryService.OccurrenceView decide(@PathVariable UUID occurrenceId,
                                              @Valid @RequestBody SubstitutionDecision request) {
        return service.decideSubstitution(occurrenceId, request.accept(),
                request.replacementSku(), request.replacementPrice());
    }

    record CreateRequest(@NotBlank String customerId, @NotBlank String postcode,
                         @NotBlank String paymentMethodToken, @Positive int cadenceWeeks,
                         @FutureOrPresent LocalDate nextDeliveryOn, boolean futurePaymentConsent,
                         @NotEmpty List<@Valid ItemRequest> items) {}
    record ItemRequest(@NotBlank String sku, @Positive int quantity,
                       @DecimalMin("0.00") BigDecimal unitPrice) {}
    record SubstitutionDecision(boolean accept, String replacementSku, BigDecimal replacementPrice) {}
}
