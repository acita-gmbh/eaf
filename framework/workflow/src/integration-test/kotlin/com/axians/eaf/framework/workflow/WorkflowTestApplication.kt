package com.axians.eaf.framework.workflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * Test application for Flowable workflow engine integration tests (Story 6.1).
 *
 * Minimal Spring Boot application that enables Flowable auto-configuration
 * for integration test validation.
 *
 * Story 6.2 Note: Excludes delegates package to avoid requiring Axon/Security beans
 * (DispatchAxonCommandTask requires CommandGateway and TenantContext which aren't
 * needed for pure Flowable engine tests).
 */
@SpringBootApplication
@ComponentScan(
    basePackages = ["com.axians.eaf.framework.workflow"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.axians\\.eaf\\.framework\\.workflow\\.delegates\\..*"],
        ),
    ],
)
class WorkflowTestApplication

fun main(args: Array<String>) {
    runApplication<WorkflowTestApplication>(*args)
}
