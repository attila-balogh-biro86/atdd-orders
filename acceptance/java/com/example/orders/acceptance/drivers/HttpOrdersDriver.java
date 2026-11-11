package com.example.orders.acceptance.drivers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Drives the service over its public HTTP API, exactly as a client would. */
public class HttpOrdersDriver implements OrdersDriver {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    public HttpOrdersDriver(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public UUID registerCustomer(String membership) {
        Response r = post("/api/customers", Map.of("membership", membership.toUpperCase()));
        if (r.status != 201) {
            throw new IllegalStateException("Customer registration failed: " + r.status + " " + r.body);
        }
        return UUID.fromString(r.body.get("id").asText());
    }

    @Override
    public OrderOutcome placeOrder(UUID customerId, BigDecimal total) {
        Response r = post("/api/orders", Map.of("customerId", customerId.toString(), "total", total));
        return switch (r.status) {
            case 201 -> new OrderOutcome.Placed(id(r.body), r.body.get("payable").decimalValue(), status(r.body));
            case 400 -> new OrderOutcome.Rejected(Reason.INVALID_ORDER, text(r.body, "detail"));
            case 404 -> new OrderOutcome.Rejected(Reason.UNKNOWN_CUSTOMER, text(r.body, "detail"));
            default -> new OrderOutcome.Rejected(Reason.OTHER, r.status + " " + r.body);
        };
    }

    @Override
    public OrderOutcome cancelOrder(UUID orderId) {
        Response r = post("/api/orders/" + orderId + "/cancel", Map.of());
        return switch (r.status) {
            case 200 -> found(r.body);
            case 404 -> isRoutingMiss(r)
                    ? new OrderOutcome.Rejected(Reason.OTHER, "cancellation is not offered by the API: " + r.body)
                    : new OrderOutcome.Rejected(Reason.UNKNOWN_ORDER, text(r.body, "detail"));
            case 409 -> new OrderOutcome.Rejected(Reason.INVALID_STATE, text(r.body, "detail"));
            default -> new OrderOutcome.Rejected(Reason.OTHER, r.status + " " + r.body);
        };
    }

    /**
     * A 404 means "unknown order" only if the operation exists. A 404 because no handler is mapped
     * for the path (the web framework's "no resource / no handler" answer) says nothing about the order
     * and must not be mistaken for the domain refusal.
     */
    private static boolean isRoutingMiss(Response r) {
        String detail = text(r.body, "detail");
        return r.body == null
                || r.body.has("error") && !r.body.has("detail")   // framework default error body
                || detail.startsWith("No static resource")
                || detail.startsWith("No endpoint");
    }

    @Override
    public OrderOutcome lookUpOrder(UUID orderId) {
        Response r = get("/api/orders/" + orderId);
        return switch (r.status) {
            case 200 -> found(r.body);
            case 404 -> new OrderOutcome.Rejected(Reason.UNKNOWN_ORDER, text(r.body, "detail"));
            default -> new OrderOutcome.Rejected(Reason.OTHER, r.status + " " + r.body);
        };
    }

    private static OrderOutcome.Found found(JsonNode body) {
        return new OrderOutcome.Found(id(body), body.get("payable").decimalValue(), status(body));
    }

    private static UUID id(JsonNode body) {
        return UUID.fromString(body.get("id").asText());
    }

    /** Maps the wire status to the domain status; null when the system does not report one. */
    private static OrderStatus status(JsonNode body) {
        if (body == null || !body.hasNonNull("status")) {
            return null;
        }
        String wire = body.get("status").asText();
        return switch (wire) {
            case "PLACED" -> OrderStatus.PLACED;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            default -> throw new IllegalStateException("Order status outside the contract: " + wire);
        };
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.has(field) ? node.get(field).asText() : "";
    }

    private record Response(int status, JsonNode body) {}

    private Response post(String path, Object payload) {
        try {
            return send(path, HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))));
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialise request to " + path, e);
        }
    }

    private Response get(String path) {
        return send(path, HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    private Response send(String path, HttpRequest.Builder builder) {
        try {
            HttpRequest req = builder
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json, application/problem+json")
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode body = res.body() == null || res.body().isBlank() ? null : json.readTree(res.body());
            return new Response(res.statusCode(), body);
        } catch (IOException e) {
            throw new IllegalStateException("HTTP call to " + baseUrl + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
