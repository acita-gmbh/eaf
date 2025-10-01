package com.axians.eaf.framework.workflow.handlers

import com.axians.eaf.framework.workflow.delegates.SecurityConfigExcludeFilter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Test application for AxonEventSignalHandler integration tests (Story 6.3).
 *
 * Separate test application from DispatchAxonCommandTestApplication to avoid
 * Kotest Multiple @SpringBootTest conflict (Story 6.2 lesson learned).
 *
 * Configuration identical to DispatchAxonCommandTestApplication but separate class
 * to satisfy Kotest's test descriptor requirements.
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.framework.workflow",
        "com.axians.eaf.framework.cqrs",
        "com.axians.eaf.framework.persistence",
        "com.axians.eaf.framework.core",
        "com.axians.eaf.products.widgetdemo",
    ],
    exclude = [
        SecurityAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
    ],
)
@EnableJpaRepositories(
    basePackages = [
        "com.axians.eaf.framework.persistence.repositories",
    ],
)
@EntityScan(
    basePackages = [
        "com.axians.eaf.framework.persistence.entities",
    ],
)
open class AxonEventSignalHandlerTestApplication {
    @Bean
    open fun securityConfigExcludeFilter(): SecurityConfigExcludeFilter = SecurityConfigExcludeFilter()
}

fun main(args: Array<String>) {
    runApplication<AxonEventSignalHandlerTestApplication>(*args)
}
