package com.example.orders.acceptance.dsl;

import com.example.orders.acceptance.drivers.OrdersDriver;
import com.example.orders.acceptance.drivers.OrdersDriver.OrderOutcome;
import com.example.orders.acceptance.drivers.OrdersDriver.OrderStatus;
import com.example.orders.acceptance.drivers.OrdersDriver.Reason;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Domain-specific language of the specification. Scenarios speak in these verbs;
 * the DSL keeps per-scenario state and delegates all I/O to the driver.
 */
@Component
@ScenarioScope
public class OrdersDsl {

    private final OrdersDriver driver;
    private UUID customerId;

    /** Outcome of the last attempt to place an order (what "the order is rejected" talks about). */
    private OrderOutcome lastPlacement;

    /** The order "the order" refers to: the last one successfully placed. */
    private UUID currentOrderId;

    /**
     * Latest thing the system told us about the current order: placement, cancellation or lookup.
     * State assertions ("the payable amount is", "the order has status") read this, never a stale placement.
     */
    private OrderOutcome currentOrderState;

    /** Outcome of the last cancellation attempt (what "the cancellation is rejected" talks about). */
    private OrderOutcome lastCancellation;

    private final Map<String, UUID> namedOrders = new HashMap<>();

    public OrdersDsl(OrdersDriver driver) {
        this.driver = driver;
    }

    // ---- customers --------------------------------------------------------------------------

    public void registerCustomer(String membership) {
        customerId = driver.registerCustomer(membership);
    }

    public void useUnregisteredCustomer() {
        customerId = UUID.randomUUID();
    }

    // ---- placing ----------------------------------------------------------------------------

    public void placeOrder(BigDecimal total) {
        assertThat(customerId).as("a customer must be chosen before ordering").isNotNull();
        lastPlacement = driver.placeOrder(customerId, total);
        currentOrderState = lastPlacement;
        if (lastPlacement instanceof OrderOutcome.Placed placed) {
            currentOrderId = placed.orderId();
        }
    }

    public void placeNamedOrder(String name, BigDecimal total) {
        placeOrder(total);
        assertThat(lastPlacement)
                .as("order \"%s\" must be placed before it can be referred to", name)
                .isInstanceOf(OrderOutcome.Placed.class);
        namedOrders.put(name, currentOrderId);
    }

    // ---- cancelling and looking up ----------------------------------------------------------

    /** Records the outcome instead of failing: a rejected cancellation is a legitimate step in a story. */
    public void cancelOrder() {
        cancel(requireCurrentOrder());
    }

    public void cancelNamedOrder(String name) {
        currentOrderId = requireNamedOrder(name);
        cancel(currentOrderId);
    }

    public void cancelUnknownOrder() {
        currentOrderId = UUID.randomUUID();
        cancel(currentOrderId);
    }

    public void lookUpOrder() {
        currentOrderState = driver.lookUpOrder(requireCurrentOrder());
    }

    private void cancel(UUID orderId) {
        lastCancellation = driver.cancelOrder(orderId);
        currentOrderState = lastCancellation;
    }

    // ---- assertions on the current order ----------------------------------------------------

    public void assertPayable(BigDecimal expected) {
        assertPayable(currentOrderState, "the order", expected);
    }

    public void assertStatus(String expected) {
        assertStatus(currentOrderState, "the order", expected);
    }

    // ---- assertions on named orders (always a fresh lookup) ---------------------------------

    public void assertNamedOrderStatus(String name, String expected) {
        assertStatus(driver.lookUpOrder(requireNamedOrder(name)), "order \"" + name + "\"", expected);
    }

    public void assertNamedOrderPayable(String name, BigDecimal expected) {
        assertPayable(driver.lookUpOrder(requireNamedOrder(name)), "order \"" + name + "\"", expected);
    }

    // ---- rejections -------------------------------------------------------------------------

    public void assertRejectedBecause(String reasonFragment) {
        OrderOutcome.Rejected rejected = assertRejected(lastPlacement, "the order");
        assertThat(rejected.reason()).isEqualTo(Reason.INVALID_ORDER);
        assertThat(rejected.detail()).containsIgnoringCase(reasonFragment);
    }

    public void assertRejectedAsUnknownCustomer() {
        assertThat(assertRejected(lastPlacement, "the order").reason()).isEqualTo(Reason.UNKNOWN_CUSTOMER);
    }

    public void assertCancellationRejectedBecause(String reasonFragment) {
        OrderOutcome.Rejected rejected = assertRejected(lastCancellation, "the cancellation");
        assertThat(rejected.reason())
                .as("cancellation rejection reason (detail: %s)", rejected.detail())
                .isEqualTo(Reason.INVALID_STATE);
        assertThat(rejected.detail()).as("cancellation rejection detail").containsIgnoringCase(reasonFragment);
    }

    public void assertCancellationRejectedAsUnknownOrder() {
        OrderOutcome.Rejected rejected = assertRejected(lastCancellation, "the cancellation");
        assertThat(rejected.reason())
                .as("cancellation rejection reason (detail: %s)", rejected.detail())
                .isEqualTo(Reason.UNKNOWN_ORDER);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static void assertPayable(OrderOutcome state, String what, BigDecimal expected) {
        assertThat(known(state, what).payable())
                .as("payable amount of %s", what)
                .isEqualByComparingTo(expected);
    }

    private static void assertStatus(OrderOutcome state, String what, String expected) {
        assertThat(known(state, what).status())
                .as("status of %s", what)
                .isEqualTo(OrderStatus.valueOf(expected.toUpperCase(Locale.ROOT)));
    }

    private static OrderOutcome.Known known(OrderOutcome state, String what) {
        assertThat(state)
                .as("expected the system to report the state of %s", what)
                .isInstanceOf(OrderOutcome.Known.class);
        return (OrderOutcome.Known) state;
    }

    private static OrderOutcome.Rejected assertRejected(OrderOutcome outcome, String what) {
        assertThat(outcome)
                .as("expected %s to be rejected", what)
                .isInstanceOf(OrderOutcome.Rejected.class);
        return (OrderOutcome.Rejected) outcome;
    }

    private UUID requireCurrentOrder() {
        assertThat(currentOrderId).as("an order must be placed first").isNotNull();
        return currentOrderId;
    }

    private UUID requireNamedOrder(String name) {
        assertThat(namedOrders).as("order \"%s\" must be placed first", name).containsKey(name);
        return namedOrders.get(name);
    }
}
