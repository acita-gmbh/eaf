package com.axians.eaf.products.widget

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Widget Demo Spring Boot Application.
 *
 * Reference implementation demonstrating EAF framework capabilities
 * with complete CQRS/ES vertical slice using Axon Framework.
 *
 * Component Scanning:
 * - Scans com.axians.eaf to include framework module configurations
 * - Picks up AxonConfiguration from framework:cqrs module
 */
@SpringBootApplication(scanBasePackages = ["com.axians.eaf"])
class WidgetDemoApplication

fun main(args: Array<String>) {
    runApplication<WidgetDemoApplication>(*args)
}
