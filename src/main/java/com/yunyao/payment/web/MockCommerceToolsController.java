package com.yunyao.payment.web;

import com.yunyao.payment.domain.Money;
import com.yunyao.payment.port.CommerceToolsPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mock/commercetools")
public class MockCommerceToolsController {
    private final CommerceToolsPort commerce;

    public MockCommerceToolsController(CommerceToolsPort commerce) {
        this.commerce = commerce;
    }

    @PutMapping("/inventory")
    void setStock(@Valid @RequestBody StockRequest request) {
        commerce.setStock(request.postcode(), request.sku(), request.quantity());
    }

    @PostMapping("/carts")
    CommerceToolsPort.CartSnapshot createCart(@Valid @RequestBody CartRequest request) {
        return commerce.createReservedCart(request.customerId(), request.postcode(),
                request.lines().stream()
                        .map(line -> new CommerceToolsPort.CartLine(
                                line.sku(), line.quantity(), Money.aud(line.unitPrice())))
                        .toList());
    }

    @GetMapping("/carts/{cartId}")
    CommerceToolsPort.CartSnapshot getCart(@PathVariable String cartId) {
        return commerce.getCart(cartId);
    }

    record StockRequest(@NotBlank String postcode, @NotBlank String sku, @PositiveOrZero int quantity) {}
    record CartRequest(@NotBlank String customerId, @NotBlank String postcode, List<@Valid CartLineRequest> lines) {}
    record CartLineRequest(@NotBlank String sku, @Positive int quantity, @NotBlank String unitPrice) {}
}
