package com.example.orders.domain;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.Scale;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit + property tests. Owned by the implementation side; mutation-tested by PIT. */
class DiscountPolicyTest {

    private final DiscountPolicy policy = new DiscountPolicy();

    @Test
    void noDiscountAtExactlyTheThreshold() {
        assertThat(policy.payable(Membership.LOYALTY, new BigDecimal("100.00")))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void discountJustAboveTheThreshold() {
        assertThat(policy.payable(Membership.LOYALTY, new BigDecimal("100.01")))
                .isEqualByComparingTo("90.01");
    }

    @Test
    void roundsHalfUp() {
        assertThat(policy.payable(Membership.LOYALTY, new BigDecimal("133.35")))
                .isEqualByComparingTo("120.02");
    }

    @Test
    void standardNeverDiscounted() {
        assertThat(policy.payable(Membership.STANDARD, new BigDecimal("999.99")))
                .isEqualByComparingTo("999.99");
    }

    // Invariants a lookup table cannot satisfy.

    @Property
    void payableNeverExceedsTotal(@ForAll Membership m,
                                  @ForAll @BigRange(min = "0.01", max = "100000") @Scale(2) BigDecimal total) {
        assertThat(policy.payable(m, total)).isLessThanOrEqualTo(total);
    }

    @Property
    void discountNeverExceedsTenPercent(@ForAll Membership m,
                                        @ForAll @BigRange(min = "0.01", max = "100000") @Scale(2) BigDecimal total) {
        BigDecimal floor = total.multiply(new BigDecimal("0.90")).subtract(new BigDecimal("0.005"));
        assertThat(policy.payable(m, total)).isGreaterThanOrEqualTo(floor);
    }

    @Property
    void payableHasCentPrecision(@ForAll Membership m,
                                 @ForAll @BigRange(min = "0.01", max = "100000") @Scale(2) BigDecimal total) {
        assertThat(policy.payable(m, total).scale()).isEqualTo(2);
    }
}
