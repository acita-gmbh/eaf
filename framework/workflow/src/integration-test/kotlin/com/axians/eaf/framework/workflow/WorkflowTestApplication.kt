package com.axians.eaf.framework.workflow

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * Test application for Flowable workflow engine integration tests (Story 6.1+).
 *
 * Minimal Spring Boot application that enables Flowable auto-configuration
 * for integration test validation.
 *
 * Story 6.2 Note: Excludes delegates package to avoid requiring Axon/Security beans
 * (DispatchAxonCommandTask requires CommandGateway and TenantContext which aren't
 * needed for pure Flowable engine tests).
 *
 * Story 6.4 Note: Excludes observability package (FlowableMetrics has @Scheduled methods
 * that cause AOP conflicts in test contexts without @EnableScheduling).
 */
@SpringBootApplication(
    exclude = [
        SecurityAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
        ManagementWebSecurityAutoConfiguration::class, // Story 6.4: Actuator requires this exclusion
    ],
)
@ComponentScan(
    basePackages = ["com.axians.eaf.framework.workflow"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.axians\\.eaf\\.framework\\.workflow\\.delegates\\..*"],
        ),
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.axians\\.eaf\\.framework\\.workflow\\.handlers\\..*"], // Story 6.3: Exclude Axon event handlers
        ),
        ComponentScan.Filter(
            type = FilterType.REGEX,
            // Story 6.4: Exclude FlowableMetrics (@Scheduled causes AOP conflicts)
            pattern = ["com\\.axians\\.eaf\\.framework\\.workflow\\.observability\\..*"],
        ),
    ],
)
class WorkflowTestApplication

fun main(args: Array<String>) {
    runApplication<WorkflowTestApplication>(*args)
}
