package com.example.orders.acceptance.config;

import com.example.orders.acceptance.drivers.EmbeddedApplication;
import com.example.orders.acceptance.drivers.HttpOrdersDriver;
import com.example.orders.acceptance.drivers.OrdersDriver;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Wires the acceptance suite. This is a plain Spring context for the TEST side only;
 * the system under test is reached exclusively through an {@link OrdersDriver}.
 *
 *   -Dacceptance.target=embedded  boots the app in-process on a random port (dev inner loop, PR CI)
 *   -Dacceptance.target=remote    talks to -Dacceptance.baseUrl (ephemeral env, staging, prod smoke)
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = AcceptanceConfig.Wiring.class)
public class AcceptanceConfig {

    @Configuration
    @ComponentScan(basePackages = "com.example.orders.acceptance.dsl")
    static class Wiring {

        @Bean
        OrdersDriver ordersDriver() {
            String target = System.getProperty("acceptance.target", "embedded");
            return switch (target) {
                case "embedded" -> new HttpOrdersDriver(EmbeddedApplication.baseUrl());
                case "remote" -> new HttpOrdersDriver(requireBaseUrl());
                default -> throw new IllegalArgumentException(
                        "Unknown acceptance.target '" + target + "' (expected embedded|remote)");
            };
        }

        private static String requireBaseUrl() {
            String url = System.getProperty("acceptance.baseUrl");
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("acceptance.target=remote requires -Dacceptance.baseUrl");
            }
            return url;
        }
    }
}
