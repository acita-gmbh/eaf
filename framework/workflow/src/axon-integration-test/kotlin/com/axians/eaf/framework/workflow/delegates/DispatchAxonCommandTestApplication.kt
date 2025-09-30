package com.axians.eaf.framework.workflow.delegates

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Test application for Flowable-to-Axon bridge integration tests (Story 6.2).
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
 * - Widget aggregate and projection handlers
 * - JPA repositories for projections
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
        "com.axians.eaf.framework.persistence",
        "com.axians.eaf.framework.core",
        // NOTE: framework.observability NOT scanned - depends on framework.security
        // NOTE: framework.security NOT scanned - all beans (@Configuration, @Component) excluded
        // TenantContext provided by AxonIntegrationTestConfig instead
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
