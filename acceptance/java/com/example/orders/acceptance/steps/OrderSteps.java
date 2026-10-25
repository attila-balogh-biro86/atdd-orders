package com.example.orders.acceptance.steps;

import com.example.orders.acceptance.dsl.OrdersDsl;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;

/**
 * Glue between Gherkin and the DSL. Every method is ONE delegation call:
 * no logic, no assertions, no HTTP. Enforced by ArchitectureRulesTest.
 */
public class OrderSteps {

    private final OrdersDsl orders;

    public OrderSteps(OrdersDsl orders) {
        this.orders = orders;
    }

    @ParameterType("-?\\d+(?:\\.\\d+)?")
    public BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    @ParameterType("loyalty|standard")
    public String membership(String value) {
        return value;
    }

    @Given("a {membership} customer")
    public void aCustomer(String membership) {
        orders.registerCustomer(membership);
    }

    @Given("an unregistered customer")
    public void anUnregisteredCustomer() {
        orders.useUnregisteredCustomer();
    }

    @When("they place an order totalling {amount} EUR")
    public void theyPlaceAnOrder(BigDecimal total) {
        orders.placeOrder(total);
    }

    @Then("the payable amount is {amount} EUR")
    public void thePayableAmountIs(BigDecimal payable) {
        orders.assertPayable(payable);
    }

    @Then("the order is rejected because {string}")
    public void theOrderIsRejectedBecause(String reason) {
        orders.assertRejectedBecause(reason);
    }

    @Then("the order is rejected because the customer is unknown")
    public void rejectedUnknownCustomer() {
        orders.assertRejectedAsUnknownCustomer();
    }
}
