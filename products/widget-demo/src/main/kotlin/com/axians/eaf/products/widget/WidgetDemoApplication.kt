package com.axians.eaf.products.widget

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Widget Demo Spring Boot Application.
 *
 * Reference implementation demonstrating EAF framework capabilities
 * with complete CQRS/ES vertical slice using Axon Framework.
 */
@SpringBootApplication
class WidgetDemoApplication

fun main(args: Array<String>) {
    runApplication<WidgetDemoApplication>(*args)
}
