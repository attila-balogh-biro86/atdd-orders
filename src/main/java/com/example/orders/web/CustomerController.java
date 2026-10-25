package com.example.orders.web;

import com.example.orders.application.OrderService;
import com.example.orders.domain.Customer;
import com.example.orders.domain.Membership;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final OrderService service;

    public CustomerController(OrderService service) {
        this.service = service;
    }

    public record RegisterCustomerRequest(@NotNull Membership membership) {}

    public record CustomerResponse(UUID id, Membership membership) {
        static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.id(), c.membership());
        }
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        Customer customer = service.registerCustomer(request.membership());
        return ResponseEntity.created(URI.create("/api/customers/" + customer.id()))
                .body(CustomerResponse.from(customer));
    }
}
