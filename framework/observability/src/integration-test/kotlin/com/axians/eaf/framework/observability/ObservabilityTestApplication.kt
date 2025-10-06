package com.axians.eaf.framework.observability

import com.axians.eaf.framework.security.tenant.TenantContext
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan

/**
 * Test application for observability framework integration tests.
 * Provides Spring Boot context for testing structured logging functionality.
 */
@SpringBootApplication(
    exclude = [
        org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration::class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration::class,
    ],
)
@ComponentScan(
    basePackages = [
        "com.axians.eaf.framework.observability",
    ],
)
class ObservabilityTestApplication {
    @Bean
    fun tenantContext(): TenantContext = TenantContext()
}

fun main(args: Array<String>) {
    runApplication<ObservabilityTestApplication>(*args)
}
