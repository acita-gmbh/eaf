package com.axians.eaf.framework.observability

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Test application for observability framework integration tests.
 * Provides Spring Boot context for testing structured logging functionality.
 */
@SpringBootApplication
class ObservabilityTestApplication

fun main(args: Array<String>) {
    runApplication<ObservabilityTestApplication>(*args)
}
