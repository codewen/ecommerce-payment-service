package com.yunyao.payment.web;

import com.yunyao.payment.application.CheckoutCommand;
import com.yunyao.payment.application.CheckoutService;
import com.yunyao.payment.application.CheckoutView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkouts")
public class CheckoutController {
    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    ResponseEntity<CheckoutView> checkout(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Valid @RequestBody CheckoutRequest request) {
        var result = checkoutService.checkout(idempotencyKey, sessionId,
                new CheckoutCommand(request.customerId(), request.cartId(), request.cartVersion(),
                        request.paymentMethodToken()));
        return ResponseEntity.created(URI.create("/api/checkouts/" + result.checkoutId())).body(result);
    }

    @GetMapping("/{checkoutId}")
    CheckoutView get(@PathVariable UUID checkoutId) {
        return checkoutService.get(checkoutId);
    }

    record CheckoutRequest(
            @NotBlank String customerId,
            @NotBlank String cartId,
            @PositiveOrZero long cartVersion,
            @NotBlank String paymentMethodToken
    ) {}
}
