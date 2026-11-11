package com.example.orders.acceptance.drivers;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Protocol driver: the ONLY way the specification touches the system.
 * Add a UiOrdersDriver (Playwright) or MessagingOrdersDriver without changing a single scenario.
 */
public interface OrdersDriver {

    UUID registerCustomer(String membership);

    OrderOutcome placeOrder(UUID customerId, BigDecimal total);

    /** Cancels the given order; a refusal is reported as {@link OrderOutcome.Rejected}, never thrown. */
    OrderOutcome cancelOrder(UUID orderId);

    /** Fetches the order's current state as the system reports it now. */
    OrderOutcome lookUpOrder(UUID orderId);

    sealed interface OrderOutcome {

        /** The system answered with the order's state. {@code status} is null if the system did not report one. */
        sealed interface Known extends OrderOutcome {
            UUID orderId();
            BigDecimal payable();
            OrderStatus status();
        }

        /** The order was created. */
        record Placed(UUID orderId, BigDecimal payable, OrderStatus status) implements Known {}

        /** The system returned an existing order's current state (after a cancellation or a lookup). */
        record Found(UUID orderId, BigDecimal payable, OrderStatus status) implements Known {}

        record Rejected(Reason reason, String detail) implements OrderOutcome {}
    }

    enum OrderStatus { PLACED, CANCELLED }

    enum Reason { INVALID_ORDER, UNKNOWN_CUSTOMER, UNKNOWN_ORDER, INVALID_STATE, OTHER }
}
