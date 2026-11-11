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

    @ParameterType("placed|cancelled")
    public String orderStatus(String value) {
        return value;
    }

    @When("they place an order {string} totalling {amount} EUR")
    public void theyPlaceANamedOrder(String name, BigDecimal total) {
        orders.placeNamedOrder(name, total);
    }

    @When("they cancel the order")
    public void theyCancelTheOrder() {
        orders.cancelOrder();
    }

    @When("they cancel order {string}")
    public void theyCancelNamedOrder(String name) {
        orders.cancelNamedOrder(name);
    }

    @When("they cancel an order that does not exist")
    public void theyCancelAnUnknownOrder() {
        orders.cancelUnknownOrder();
    }

    @When("they look up the order")
    public void theyLookUpTheOrder() {
        orders.lookUpOrder();
    }

    @Then("the order has status {orderStatus}")
    public void theOrderHasStatus(String status) {
        orders.assertStatus(status);
    }

    @Then("order {string} has status {orderStatus}")
    public void namedOrderHasStatus(String name, String status) {
        orders.assertNamedOrderStatus(name, status);
    }

    @Then("order {string} has a payable amount of {amount} EUR")
    public void namedOrderHasPayable(String name, BigDecimal payable) {
        orders.assertNamedOrderPayable(name, payable);
    }

    @Then("the cancellation is rejected because {string}")
    public void theCancellationIsRejectedBecause(String reason) {
        orders.assertCancellationRejectedBecause(reason);
    }

    @Then("the cancellation is rejected because the order is unknown")
    public void cancellationRejectedUnknownOrder() {
        orders.assertCancellationRejectedAsUnknownOrder();
    }
}
