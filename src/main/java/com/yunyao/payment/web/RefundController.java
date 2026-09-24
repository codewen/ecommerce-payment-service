package com.yunyao.payment.web;

import com.yunyao.payment.application.RefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkouts/{checkoutId}/refunds")
public class RefundController {
    private final RefundService refunds;

    public RefundController(RefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping
    RefundService.RefundView refund(@PathVariable UUID checkoutId,
                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                    @Valid @RequestBody RefundRequest request) {
        return refunds.refund(checkoutId, idempotencyKey, request.amount(), request.reason());
    }

    record RefundRequest(@DecimalMin("0.01") BigDecimal amount, @NotBlank String reason) {}
}
