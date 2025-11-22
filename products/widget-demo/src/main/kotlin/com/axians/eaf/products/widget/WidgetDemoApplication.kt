package com.axians.eaf.products.widget

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Widget Demo Spring Boot Application.
 *
 * Reference implementation demonstrating EAF framework capabilities
 * with complete CQRS/ES vertical slice using Axon Framework.
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.products.widget",
        "com.axians.eaf.framework.security",
        "com.axians.eaf.framework.multitenancy", // Story 4.6: Enable TenantContextFilter and interceptors
    ],
)
class WidgetDemoApplication

fun main(args: Array<String>) {
    runApplication<WidgetDemoApplication>(*args)
}
