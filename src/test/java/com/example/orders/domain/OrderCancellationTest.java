package com.example.orders.domain;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.Scale;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-ORD-015: unit + property tests for the order lifecycle. */
class OrderCancellationTest {

    private final DiscountPolicy policy = new DiscountPolicy();

    private Order placed(Membership membership, String total) {
        return Order.place(Customer.register(membership), new BigDecimal(total), policy);
    }

    @Test
    void newlyPlacedOrderIsPlaced() {
        assertThat(placed(Membership.STANDARD, "12.34").status()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void cancellingPlacedOrderMakesItCancelledWithNothingPayable() {
        Order order = placed(Membership.LOYALTY, "250.00");
        Order cancelled = order.cancel();
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.payable()).isEqualByComparingTo("0.00");
        assertThat(cancelled.payable().scale()).isEqualTo(2);
    }

    @Test
    void cancellingKeepsIdentityCustomerAndTotal() {
        Order order = placed(Membership.STANDARD, "42.00");
        Order cancelled = order.cancel();
        assertThat(cancelled.id()).isEqualTo(order.id());
        assertThat(cancelled.customerId()).isEqualTo(order.customerId());
        assertThat(cancelled.total()).isEqualByComparingTo(order.total());
    }

    @Test
    void cancellingDoesNotMutateTheOriginal() {
        Order order = placed(Membership.STANDARD, "42.00");
        order.cancel();
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.payable()).isEqualByComparingTo("42.00");
    }

    @Test
    void cancellingAnAlreadyCancelledOrderIsRejected() {
        Order cancelled = placed(Membership.STANDARD, "42.00").cancel();
        assertThatThrownBy(cancelled::cancel)
                .isInstanceOf(OrderAlreadyCancelledException.class)
                .hasMessageContaining("already cancelled")
                .hasMessageContaining(cancelled.id().toString());
    }

    @Test
    void statusIsRequired() {
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ONE, BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status");
    }

    @Test
    void unknownOrderMessageNamesTheOrder() {
        UUID id = UUID.randomUUID();
        assertThat(new OrderNotFoundException(id)).hasMessageContaining(id.toString());
    }

    @Property
    void anyPlacedOrderCancelsToZero(@ForAll Membership m,
                                     @ForAll @BigRange(min = "0.01", max = "100000") @Scale(2) BigDecimal total) {
        Order cancelled = Order.place(Customer.register(m), total, policy).cancel();
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.payable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cancelled.total()).isEqualByComparingTo(total);
    }

    @Property
    void cancelledOrderCannotBeCancelledAgain(@ForAll Membership m,
                                              @ForAll @BigRange(min = "0.01", max = "100000") @Scale(2) BigDecimal total) {
        Order cancelled = Order.place(Customer.register(m), total, policy).cancel();
        assertThatThrownBy(cancelled::cancel).isInstanceOf(OrderAlreadyCancelledException.class);
    }
}
