package com.example.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Loyalty discount tiers. Amounts are EUR with 2 decimals, rounded HALF_UP.
 * <ul>
 *   <li>REQ-ORD-012: loyalty members get 10% off orders strictly above 100.00 EUR.</li>
 *   <li>REQ-ORD-017: loyalty members get 15% off orders strictly above 1000.00 EUR
 *       (applied to the whole total, replacing the 10%).</li>
 *   <li>Standard customers never get a discount.</li>
 * </ul>
 */
public final class DiscountPolicy {

    static final BigDecimal THRESHOLD = new BigDecimal("100.00");
    static final BigDecimal LOYALTY_FACTOR = new BigDecimal("0.90");
    static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000.00");
    static final BigDecimal LARGE_ORDER_LOYALTY_FACTOR = new BigDecimal("0.85");

    public BigDecimal payable(Membership membership, BigDecimal total) {
        BigDecimal normalized = total.setScale(2, RoundingMode.HALF_UP);
        if (membership != Membership.LOYALTY) {
            return normalized;
        }
        if (normalized.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            return applyFactor(normalized, LARGE_ORDER_LOYALTY_FACTOR);
        }
        if (normalized.compareTo(THRESHOLD) > 0) {
            return applyFactor(normalized, LOYALTY_FACTOR);
        }
        return normalized;
    }

    private static BigDecimal applyFactor(BigDecimal amount, BigDecimal factor) {
        return amount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
