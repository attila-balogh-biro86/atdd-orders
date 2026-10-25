package com.example.orders.acceptance.drivers;

import com.example.orders.OrdersApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots the real application once per JVM on a random port. The suite still talks HTTP,
 * so "embedded" and "remote" runs exercise exactly the same path.
 */
public final class EmbeddedApplication {

    private static ConfigurableApplicationContext context;

    private EmbeddedApplication() {}

    public static synchronized String baseUrl() {
        if (context == null) {
            context = SpringApplication.run(OrdersApplication.class,
                    "--server.port=0", "--spring.main.banner-mode=off");
            Runtime.getRuntime().addShutdownHook(new Thread(context::close));
        }
        return "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }
}
