package com.example.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * REQ-ORD-012: loyalty members get 10% off orders strictly above 100.00 EUR.
 * Amounts are EUR with 2 decimals, rounded HALF_UP.
 */
public final class DiscountPolicy {

    static final BigDecimal THRESHOLD = new BigDecimal("100.00");
    static final BigDecimal LOYALTY_FACTOR = new BigDecimal("0.90");

    public BigDecimal payable(Membership membership, BigDecimal total) {
        BigDecimal normalized = total.setScale(2, RoundingMode.HALF_UP);
        if (membership == Membership.LOYALTY && normalized.compareTo(THRESHOLD) > 0) {
            return normalized.multiply(LOYALTY_FACTOR).setScale(2, RoundingMode.HALF_UP);
        }
        return normalized;
    }
}
