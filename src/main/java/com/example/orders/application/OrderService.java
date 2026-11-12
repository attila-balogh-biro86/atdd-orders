package com.example.orders.application;

import com.example.orders.domain.Customer;
import com.example.orders.domain.CustomerNotFoundException;
import com.example.orders.domain.DiscountPolicy;
import com.example.orders.domain.Membership;
import com.example.orders.domain.Order;
import com.example.orders.domain.OrderNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service. Storage is in-memory on purpose: the reference project is about
 * the delivery workflow, not persistence. Swap for Mongo/Postgres + Testcontainers.
 */
@Service
public class OrderService {

    private final Map<UUID, Customer> customers = new ConcurrentHashMap<>();
    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();
    private final DiscountPolicy discountPolicy = new DiscountPolicy();

    public Customer registerCustomer(Membership membership) {
        Customer customer = Customer.register(membership);
        customers.put(customer.id(), customer);
        return customer;
    }

    public Order placeOrder(UUID customerId, BigDecimal total) {
        Customer customer = Optional.ofNullable(customers.get(customerId))
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        Order order = Order.place(customer, total, discountPolicy);
        orders.put(order.id(), order);
        return order;
    }

    /**
     * REQ-ORD-015: cancels the order atomically, so two concurrent cancellations cannot both succeed.
     *
     * @throws OrderNotFoundException if no such order exists
     * @throws com.example.orders.domain.OrderAlreadyCancelledException if it is already cancelled
     */
    public Order cancelOrder(UUID id) {
        Order cancelled = orders.computeIfPresent(id, (key, order) -> order.cancel());
        if (cancelled == null) {
            throw new OrderNotFoundException(id);
        }
        return cancelled;
    }

    public Optional<Order> findOrder(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }
}
