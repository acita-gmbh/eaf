package com.axians.eaf.framework.workflow.delegates

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/**
 * Test application for Flowable-to-Axon bridge integration tests (Story 6.2).
 *
 * ## Architecture: Framework Test Independence
 *
 * Framework tests MUST NOT depend on products. This test app uses framework-local test types:
 * - TestEntityAggregate (framework test aggregate)
 * - CreateTestEntityCommand (framework test command)
 * - TestEntityCreatedEvent (framework test event)
 * - TestEntityProjectionHandler (framework test handler, in-memory only)
 *
 * This validates the generic infrastructure works without coupling to Widget domain.
 *
 * ## Configuration Strategy (Research-Driven)
 *
 * This test app uses TypeExcludeFilter (SecurityConfigExcludeFilter) to block security
 * @Configuration classes BEFORE they're scanned. This allows broader component scanning
 * while preventing security bean creation.
 *
 * **Enabled Components**:
 * - Flowable BPMN engine (from Story 6.1)
 * - Axon Framework in-memory mode (axon.axonserver.enabled=false)
 * - Framework test aggregate and projection handlers (in-memory)
 * - TenantContext (via AxonIntegrationTestConfig)
 * - MeterRegistry (via AxonIntegrationTestConfig)
 *
 * **Blocked Components** (via TypeExcludeFilter):
 * - framework.security.config.SecurityConfiguration
 * - framework.security.config.SecurityFilterChainConfiguration
 * - All other @Configuration from framework.security package
 *
 * Research: 4 external AI sources confirm TypeExcludeFilter as most powerful isolation tool.
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
open class DispatchAxonCommandTestApplication {
    /**
     * Register TypeExcludeFilter as a bean to block security configurations.
     */
    @Bean
    open fun securityConfigExcludeFilter(): SecurityConfigExcludeFilter = SecurityConfigExcludeFilter()
}

fun main(args: Array<String>) {
    runApplication<DispatchAxonCommandTestApplication>(*args)
}
