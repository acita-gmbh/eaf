package com.axians.eaf.framework.workflow.observability

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration Properties for Flowable Metrics Collection
 *
 * Provides type-safe, validated configuration for Flowable observability metrics
 * collection intervals. Uses modern Spring Boot 3.5 @ConfigurationProperties with
 * Kotlin data class pattern for immutability and compile-time type safety.
 *
 * **Usage** (application.yml):
 * ```yaml
 * eaf:
 *   workflow:
 *     metrics:
 *       process-instance-interval-ms: 30000
 *       dead-letter-queue-interval-ms: 60000
 * ```
 *
 * **Story Context:** Story 6.4 remediation (PR feedback) - Make metrics intervals
 * configurable to support environment-specific tuning (dev: 5s, prod: 30s, etc.)
 *
 * **Design Rationale:**
 * - **Kotlin data class** - Immutable, concise, auto-generates equals/hashCode/toString
 * - **JSR-303 validation** - Fail-fast at startup if intervals < 1 second
 * - **@ConfigurationProperties** - Type-safe alternative to @Value (Spring Boot 3 best practice)
 *
 * @property processInstanceIntervalMs Interval (milliseconds) for collecting active/suspended process instance metrics.
 *   Default: 30000ms (30 seconds). Recommended: Dev=5000, Staging=15000, Prod=30000.
 *   Validated: Must be >= 1000ms.
 * @property deadLetterQueueIntervalMs Interval (milliseconds) for collecting dead letter queue depth metrics.
 *   Default: 60000ms (60 seconds). Recommended: Dev=10000, Staging=30000, Prod=60000.
 *   Alert thresholds: > 100 jobs (critical), > 1000 jobs (rollback trigger).
 *   Validated: Must be >= 1000ms.
 */
@ConfigurationProperties(prefix = "eaf.workflow.metrics")
@Validated
data class FlowableMetricsProperties(
    @field:Min(value = 1000, message = "Process instance interval must be at least 1 second (1000ms)")
    val processInstanceIntervalMs: Long = 30000,
    @field:Min(value = 1000, message = "Dead letter queue interval must be at least 1 second (1000ms)")
    val deadLetterQueueIntervalMs: Long = 60000,
)
