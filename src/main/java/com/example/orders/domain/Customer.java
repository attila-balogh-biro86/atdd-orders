package com.example.orders.domain;

import java.util.Objects;
import java.util.UUID;

public record Customer(UUID id, Membership membership) {
    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(membership, "membership");
    }

    public static Customer register(Membership membership) {
        return new Customer(UUID.randomUUID(), membership);
    }
}
