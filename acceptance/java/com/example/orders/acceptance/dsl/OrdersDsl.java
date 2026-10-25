package com.example.orders.acceptance.dsl;

import com.example.orders.acceptance.drivers.OrdersDriver;
import com.example.orders.acceptance.drivers.OrdersDriver.OrderOutcome;
import com.example.orders.acceptance.drivers.OrdersDriver.Reason;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    private OrderOutcome lastOutcome;

    public OrdersDsl(OrdersDriver driver) {
        this.driver = driver;
    }

    public void registerCustomer(String membership) {
        customerId = driver.registerCustomer(membership);
    }

    public void useUnregisteredCustomer() {
        customerId = UUID.randomUUID();
    }

    public void placeOrder(BigDecimal total) {
        assertThat(customerId).as("a customer must be chosen before ordering").isNotNull();
        lastOutcome = driver.placeOrder(customerId, total);
    }

    public void assertPayable(BigDecimal expected) {
        assertThat(lastOutcome)
                .as("expected the order to be placed")
                .isInstanceOf(OrderOutcome.Placed.class);
        assertThat(((OrderOutcome.Placed) lastOutcome).payable())
                .as("payable amount")
                .isEqualByComparingTo(expected);
    }

    public void assertRejectedBecause(String reasonFragment) {
        OrderOutcome.Rejected rejected = assertRejected();
        assertThat(rejected.reason()).isEqualTo(Reason.INVALID_ORDER);
        assertThat(rejected.detail()).containsIgnoringCase(reasonFragment);
    }

    public void assertRejectedAsUnknownCustomer() {
        assertThat(assertRejected().reason()).isEqualTo(Reason.UNKNOWN_CUSTOMER);
    }

    private OrderOutcome.Rejected assertRejected() {
        assertThat(lastOutcome)
                .as("expected the order to be rejected")
                .isInstanceOf(OrderOutcome.Rejected.class);
        return (OrderOutcome.Rejected) lastOutcome;
    }
}
