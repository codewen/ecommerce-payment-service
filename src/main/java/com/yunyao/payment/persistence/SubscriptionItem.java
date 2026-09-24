package com.yunyao.payment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class SubscriptionItem {
    @Column(name = "sku", nullable = false)
    private String sku;
    @Column(name = "quantity", nullable = false)
    private int quantity;
    @Column(name = "last_known_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal lastKnownUnitPrice;

    protected SubscriptionItem() {}

    public SubscriptionItem(String sku, int quantity, BigDecimal lastKnownUnitPrice) {
        this.sku = sku;
        this.quantity = quantity;
        this.lastKnownUnitPrice = lastKnownUnitPrice;
    }

    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLastKnownUnitPrice() { return lastKnownUnitPrice; }
}
