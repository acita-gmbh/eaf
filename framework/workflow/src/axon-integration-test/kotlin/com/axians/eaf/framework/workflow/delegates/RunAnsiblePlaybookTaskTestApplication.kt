package com.axians.eaf.framework.workflow.delegates

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Test application for RunAnsiblePlaybookTask integration tests (Story 6.4, Task 8).
 *
 * **Enabled Components**:
 * - Flowable BPMN engine
 * - FlowableMetrics (requires @EnableScheduling)
 * - RunAnsiblePlaybookTask delegate
 * - AnsibleExecutor (SSH infrastructure)
 * - TenantContext (via AxonIntegrationTestConfig)
 * - Axon Framework in-memory mode
 *
 * **Blocked Components**:
 * - Security configuration (via SecurityConfigExcludeFilter)
 * - OAuth2 resource server
 *
 * Story 6.4 (Task 8) - Ansible Integration Test Infrastructure
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.framework.workflow",
        "com.axians.eaf.framework.cqrs",
        "com.axians.eaf.framework.core",
    ],
    exclude = [
        SecurityAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
    ],
)
@EnableScheduling // Required for FlowableMetrics @Scheduled methods
open class RunAnsiblePlaybookTaskTestApplication {
    /**
     * Register TypeExcludeFilter to block security configurations.
     */
    @Bean
    open fun securityConfigExcludeFilter(): SecurityConfigExcludeFilter = SecurityConfigExcludeFilter()
}

fun main(args: Array<String>) {
    runApplication<RunAnsiblePlaybookTaskTestApplication>(*args)
}
