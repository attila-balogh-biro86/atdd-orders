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

    sealed interface OrderOutcome {
        record Placed(UUID orderId, BigDecimal payable) implements OrderOutcome {}
        record Rejected(Reason reason, String detail) implements OrderOutcome {}
    }

    enum Reason { INVALID_ORDER, UNKNOWN_CUSTOMER, OTHER }
}
