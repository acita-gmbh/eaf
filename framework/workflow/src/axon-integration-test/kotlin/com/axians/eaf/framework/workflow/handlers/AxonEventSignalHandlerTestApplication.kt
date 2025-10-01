package com.axians.eaf.framework.workflow.handlers

import com.axians.eaf.framework.workflow.delegates.SecurityConfigExcludeFilter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/**
 * Test application for AxonEventSignalHandler integration tests (Story 6.3).
 *
 * ## Architecture: Framework Test Independence
 *
 * Framework tests MUST NOT depend on products. This test app uses framework-local test types
 * to validate the generic Axon→Flowable bridge infrastructure.
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
        "com.axians.eaf.framework.core",
        // NOTE: framework.observability NOT scanned - depends on framework.security
        // NOTE: framework.security NOT scanned - all beans (@Configuration, @Component) excluded
        // TenantContext provided by AxonIntegrationTestConfig instead
    ],
    exclude = [
        SecurityAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
    ],
)
open class AxonEventSignalHandlerTestApplication {
    @Bean
    open fun securityConfigExcludeFilter(): SecurityConfigExcludeFilter = SecurityConfigExcludeFilter()
}

fun main(args: Array<String>) {
    runApplication<AxonEventSignalHandlerTestApplication>(*args)
}
