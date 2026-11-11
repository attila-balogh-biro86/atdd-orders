package com.example.orders.application;

import com.example.orders.domain.Membership;
import com.example.orders.domain.Order;
import com.example.orders.domain.OrderAlreadyCancelledException;
import com.example.orders.domain.OrderNotFoundException;
import com.example.orders.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceCancellationTest {

    private final OrderService service = new OrderService();

    @Test
    void cancelledOrderIsPersistedAndCanBeLookedUp() {
        UUID customer = service.registerCustomer(Membership.LOYALTY).id();
        Order order = service.placeOrder(customer, new BigDecimal("300.00"));

        Order cancelled = service.cancelOrder(order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(service.findOrder(order.id())).contains(cancelled);
    }

    @Test
    void secondCancellationIsRejectedAndLeavesOrderCancelled() {
        UUID customer = service.registerCustomer(Membership.STANDARD).id();
        Order order = service.placeOrder(customer, new BigDecimal("10.00"));
        Order cancelled = service.cancelOrder(order.id());

        assertThatThrownBy(() -> service.cancelOrder(order.id()))
                .isInstanceOf(OrderAlreadyCancelledException.class);
        assertThat(service.findOrder(order.id())).contains(cancelled);
    }

    @Test
    void unknownOrderCannotBeCancelled() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> service.cancelOrder(unknown))
                .isInstanceOf(OrderNotFoundException.class);
        assertThat(service.findOrder(unknown)).isEmpty();
    }

    @Test
    void cancellingOneOrderLeavesOthersUntouched() {
        UUID customer = service.registerCustomer(Membership.LOYALTY).id();
        Order a = service.placeOrder(customer, new BigDecimal("20.00"));
        Order b = service.placeOrder(customer, new BigDecimal("120.00"));

        service.cancelOrder(a.id());

        assertThat(service.findOrder(b.id())).contains(b);
    }
}
