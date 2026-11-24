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

    @Test
    void tenPercentAtExactlyTheLargeOrderThreshold() {
        assertThat(policy.payable(Membership.LOYALTY, new BigDecimal("1000.00")))
                .isEqualByComparingTo("900.00");
    }

    @Test
    void fifteenPercentJustAboveTheLargeOrderThreshold() {
        assertThat(policy.payable(Membership.LOYALTY, new BigDecimal("1000.01")))
                .isEqualByComparingTo("850.01");
    }

    @Test
    void largeOrderRoundsHalfUp() {
        // 1000.10 * 0.85 = 850.085 -> 850.09
        assertThat(policy.payable(Membership.LOYALTY, new BigDecimal("1000.10")))
                .isEqualByComparingTo("850.09");
    }

    @Test
    void standardNotDiscountedAboveLargeOrderThreshold() {
        assertThat(policy.payable(Membership.STANDARD, new BigDecimal("1000.01")))
                .isEqualByComparingTo("1000.01");
    }

    @Test
    void inputIsNormalisedToCentsHalfUpBeforeTierSelection() {
        // 1000.005 normalises to 1000.01, which is strictly above the large-order threshold
        assertThat(policy.payable(Membership.LOYALTY, new BigDecimal("1000.005")))
                .isEqualByComparingTo("850.01");
        assertThat(policy.payable(Membership.STANDARD, new BigDecimal("1000.005")))
                .isEqualByComparingTo("1000.01");
    }

    // Invariants a lookup table cannot satisfy.

    @Property
    void payableNeverExceedsTotal(@ForAll Membership m,
                                  @ForAll @BigRange(min = "0.01", max = "100000") @Scale(2) BigDecimal total) {
        assertThat(policy.payable(m, total)).isLessThanOrEqualTo(total);
    }

    @Property
    void loyaltyLargeOrdersPayExactlyEightyFivePercentRoundedHalfUp(
            @ForAll @BigRange(min = "1000.01", max = "100000") @Scale(2) BigDecimal total) {
        BigDecimal expected = total.multiply(new BigDecimal("0.85")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(policy.payable(Membership.LOYALTY, total)).isEqualByComparingTo(expected);
    }

    @Property
    void loyaltyMidOrdersPayExactlyNinetyPercentRoundedHalfUp(
            @ForAll @BigRange(min = "100.01", max = "1000.00") @Scale(2) BigDecimal total) {
        BigDecimal expected = total.multiply(new BigDecimal("0.90")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(policy.payable(Membership.LOYALTY, total)).isEqualByComparingTo(expected);
    }

    @Property
    void loyaltySmallOrdersPayFullAmount(
            @ForAll @BigRange(min = "0.01", max = "100.00") @Scale(2) BigDecimal total) {
        assertThat(policy.payable(Membership.LOYALTY, total)).isEqualByComparingTo(total);
    }

    @Property
    void standardCustomersAlwaysPayFullAmount(
            @ForAll @BigRange(min = "0.01", max = "100000") @Scale(2) BigDecimal total) {
        assertThat(policy.payable(Membership.STANDARD, total)).isEqualByComparingTo(total);
    }

    @Property
    void discountNeverExceedsFifteenPercent(@ForAll Membership m,
                                            @ForAll @BigRange(min = "0.01", max = "100000") @Scale(2) BigDecimal total) {
        BigDecimal floor = total.multiply(new BigDecimal("0.85")).subtract(new BigDecimal("0.005"));
        assertThat(policy.payable(m, total)).isGreaterThanOrEqualTo(floor);
    }

    @Property
    void discountAtMostTenPercentUpToLargeOrderThreshold(@ForAll Membership m,
                                                         @ForAll @BigRange(min = "0.01", max = "1000.00") @Scale(2) BigDecimal total) {
        BigDecimal floor = total.multiply(new BigDecimal("0.90")).subtract(new BigDecimal("0.005"));
        assertThat(policy.payable(m, total)).isGreaterThanOrEqualTo(floor);
    }

    @Property
    void payableHasCentPrecision(@ForAll Membership m,
                                 @ForAll @BigRange(min = "0.01", max = "100000") @Scale(2) BigDecimal total) {
        assertThat(policy.payable(m, total).scale()).isEqualTo(2);
    }
}
