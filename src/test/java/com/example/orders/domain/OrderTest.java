package com.example.orders.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private final DiscountPolicy policy = new DiscountPolicy();
    private final Customer loyal = Customer.register(Membership.LOYALTY);

    @Test
    void placesOrderWithDiscount() {
        Order order = Order.place(loyal, new BigDecimal("200"), policy);
        assertThat(order.total()).isEqualByComparingTo("200.00");
        assertThat(order.payable()).isEqualByComparingTo("180.00");
        assertThat(order.customerId()).isEqualTo(loyal.id());
    }

    @Test
    void rejectsZeroTotal() {
        assertThatThrownBy(() -> Order.place(loyal, BigDecimal.ZERO, policy))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("greater than 0");
    }

    @Test
    void rejectsNegativeTotal() {
        assertThatThrownBy(() -> Order.place(loyal, new BigDecimal("-1"), policy))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void rejectsSubCentPrecision() {
        assertThatThrownBy(() -> Order.place(loyal, new BigDecimal("10.001"), policy))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("at most 2 decimals");
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimals() {
        assertThat(Order.place(loyal, new BigDecimal("10.000"), policy).total())
                .isEqualByComparingTo("10.00");
    }
}
