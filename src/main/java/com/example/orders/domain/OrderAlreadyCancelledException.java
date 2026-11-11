package com.example.orders.domain;

import java.util.UUID;

/** REQ-ORD-015: a cancelled order cannot be cancelled again. */
public class OrderAlreadyCancelledException extends RuntimeException {
    public OrderAlreadyCancelledException(UUID id) {
        super("Order " + id + " is already cancelled");
    }
}
