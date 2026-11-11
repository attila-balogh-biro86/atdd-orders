package com.example.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/** Immutable order. State changes return a new instance. */
public record Order(UUID id, UUID customerId, BigDecimal total, BigDecimal payable, OrderStatus status) {

    static final BigDecimal NOTHING_PAYABLE = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(payable, "payable");
        Objects.requireNonNull(status, "status");
    }

    /** REQ-ORD-013: an order total must be a positive amount with at most 2 decimals. */
    public static Order place(Customer customer, BigDecimal total, DiscountPolicy policy) {
        if (total == null || total.signum() <= 0) {
            throw new InvalidOrderException("Order total must be greater than 0");
        }
        if (total.stripTrailingZeros().scale() > 2) {
            throw new InvalidOrderException("Order total must have at most 2 decimals");
        }
        BigDecimal normalized = total.setScale(2, RoundingMode.UNNECESSARY);
        return new Order(UUID.randomUUID(), customer.id(), normalized,
                policy.payable(customer.membership(), normalized), OrderStatus.PLACED);
    }

    /**
     * REQ-ORD-015: a placed order can be cancelled; it then has status cancelled and nothing is payable.
     * A cancelled order cannot be cancelled again.
     */
    public Order cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(id);
        }
        return new Order(id, customerId, total, NOTHING_PAYABLE, OrderStatus.CANCELLED);
    }
}
