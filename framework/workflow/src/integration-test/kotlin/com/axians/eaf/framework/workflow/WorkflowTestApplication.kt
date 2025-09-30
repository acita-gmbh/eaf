package com.axians.eaf.framework.workflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Test application for Flowable workflow engine integration tests (Story 6.1).
 *
 * Minimal Spring Boot application that enables Flowable auto-configuration
 * for integration test validation.
 */
@SpringBootApplication
class WorkflowTestApplication

fun main(args: Array<String>) {
    runApplication<WorkflowTestApplication>(*args)
}
