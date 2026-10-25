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
            case 201 -> new OrderOutcome.Placed(
                    UUID.fromString(r.body.get("id").asText()),
                    r.body.get("payable").decimalValue());
            case 400 -> new OrderOutcome.Rejected(Reason.INVALID_ORDER, text(r.body, "detail"));
            case 404 -> new OrderOutcome.Rejected(Reason.UNKNOWN_CUSTOMER, text(r.body, "detail"));
            default -> new OrderOutcome.Rejected(Reason.OTHER, r.status + " " + r.body);
        };
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.has(field) ? node.get(field).asText() : "";
    }

    private record Response(int status, JsonNode body) {}

    private Response post(String path, Object payload) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, application/problem+json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
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
