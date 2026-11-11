package com.example.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Component scanning is limited to production packages on purpose. During the acceptance run the
 * test classes (com.example.orders.acceptance..) are on the same classpath; scanning them would pull
 * the suite's own wiring into the application and make it boot itself recursively.
 */
@SpringBootApplication(scanBasePackages = {
        "com.example.orders.application",
        "com.example.orders.web"
})
public class OrdersApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }
}
