package com.example.orders.web;

import com.example.orders.application.OrderService;
import com.example.orders.domain.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    public record PlaceOrderRequest(@NotNull UUID customerId, @NotNull BigDecimal total) {}

    public record OrderResponse(UUID id, UUID customerId, BigDecimal total, BigDecimal payable) {
        static OrderResponse from(Order o) {
            return new OrderResponse(o.id(), o.customerId(), o.total(), o.payable());
        }
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        Order order = service.placeOrder(request.customerId(), request.total());
        return ResponseEntity.created(URI.create("/api/orders/" + order.id()))
                .body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id) {
        return service.findOrder(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
