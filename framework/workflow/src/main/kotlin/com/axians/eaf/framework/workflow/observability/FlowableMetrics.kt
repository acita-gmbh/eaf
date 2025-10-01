package com.axians.eaf.framework.workflow.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.flowable.engine.ProcessEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Flowable Metrics Component
 *
 * Provides Prometheus metrics for Flowable BPMN workflow monitoring including:
 * - Process instance lifecycle metrics (active, suspended, completed by process definition)
 * - Process execution duration with process_key and status tags
 * - BPMN error rate tracking for rollback trigger monitoring
 * - Message/signal delivery success rate
 * - Dead letter queue depth with alerting threshold
 *
 * **Operational Integration:**
 * - Rollback trigger: BPMN error rate > 10% over 15min
 * - Alert threshold: Dead letter queue depth > 100 jobs
 * - Performance baseline: p95 process duration < 30s
 *
 * **Story Context:** Story 6.4 (Subtasks 1.1-1.6) - Addresses Epic 6 PO validation gaps
 * for Flowable observability before adding Ansible complexity.
 *
 * @param meterRegistry Micrometer registry for Prometheus metrics
 * @param processEngine Flowable ProcessEngine for runtime queries
 */
@Component
@ConditionalOnProperty(
    prefix = "eaf.workflow",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class FlowableMetrics(
    private val meterRegistry: MeterRegistry,
    private val processEngine: ProcessEngine,
) {
    private val logger: Logger = LoggerFactory.getLogger(FlowableMetrics::class.java)

    /**
     * METRIC 1 & 2: Active and Suspended Process Instances (Subtasks 1.2)
     *
     * Records active process instances grouped by process definition key.
     * Suspended instances indicate potential workflow issues requiring investigation.
     *
     * Scheduled execution: Every 30 seconds
     * Alert threshold: Suspended instances > 0 (investigate immediately)
     */
    @Scheduled(fixedRate = 30000) // Every 30s
    fun recordProcessInstanceMetrics() {
        try {
            val runtime = processEngine.runtimeService

            // Active instances grouped by process definition key
            val activeInstances =
                runtime
                    .createProcessInstanceQuery()
                    .active()
                    .list()

            activeInstances
                .groupBy { it.processDefinitionKey }
                .forEach { (key, instances) ->
                    Gauge
                        .builder("flowable.process.instances.active", instances) { it.size.toDouble() }
                        .tag("process_key", key ?: "unknown")
                        .description("Number of active process instances by process definition")
                        .register(meterRegistry)
                }

            // Total active instances
            meterRegistry.gauge(
                "flowable.process.instances.active.total",
                activeInstances.size.toDouble(),
            )

            // Suspended instances (potential issues)
            val suspendedCount = runtime.createProcessInstanceQuery().suspended().count()
            meterRegistry.gauge(
                "flowable.process.instances.suspended",
                suspendedCount.toDouble(),
            )

            if (suspendedCount > 0) {
                logger.warn("Suspended process instances detected: {} instances", suspendedCount)
            }

            logger.trace(
                "Process instance metrics recorded: {} active, {} suspended",
                activeInstances.size,
                suspendedCount,
            )
        } catch (ex: Exception) {
            logger.error("Failed to record process instance metrics", ex)
        }
    }

    /**
     * METRIC 3: Process Execution Duration (Subtask 1.3)
     *
     * Records process execution duration with process_key and status tags.
     * Used for performance monitoring and p95 latency alerting.
     *
     * **Performance Baseline:** p95 < 30s (alert if exceeded)
     *
     * @param processInstanceId Unique process instance identifier
     * @param durationMs Process execution duration in milliseconds
     * @param processKey Process definition key for grouping
     */
    fun recordProcessDuration(
        processInstanceId: String,
        durationMs: Long,
        processKey: String,
    ) {
        try {
            Timer
                .builder("flowable.process.duration")
                .tag("process_key", processKey)
                .tag("status", "completed")
                .description("Process execution duration in milliseconds")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS)

            logger.trace(
                "Process duration recorded: instance={}, duration={}ms, key={}",
                processInstanceId,
                durationMs,
                processKey,
            )
        } catch (ex: Exception) {
            logger.error(
                "Failed to record process duration for instance: {}",
                processInstanceId,
                ex,
            )
        }
    }

    /**
     * METRIC 4: BPMN Error Rates (Subtask 1.4)
     *
     * Records BPMN error occurrences with error_code and process_key tags.
     * Critical for rollback trigger monitoring.
     *
     * **Rollback Trigger:** Error rate > 10% over 15min window
     *
     * @param errorCode BPMN error code (e.g., "ANSIBLE_FAILED", "MISSING_VARIABLE")
     * @param processKey Process definition key where error occurred
     */
    fun recordBpmnError(
        errorCode: String,
        processKey: String,
    ) {
        try {
            Counter
                .builder("flowable.process.errors")
                .tag("error_code", errorCode)
                .tag("process_key", processKey)
                .description("Count of BPMN errors by error code and process")
                .register(meterRegistry)
                .increment()

            logger.warn(
                "BPMN error recorded: error_code={}, process_key={}",
                errorCode,
                processKey,
            )
        } catch (ex: Exception) {
            logger.error(
                "Failed to record BPMN error: error_code={}, process_key={}",
                errorCode,
                processKey,
                ex,
            )
        }
    }

    /**
     * METRIC 5: Signal Delivery Success Rate (Subtask 1.5)
     *
     * Records message/signal delivery outcomes for Axon→Flowable bridge monitoring.
     * Used to track signal delivery reliability from AxonEventSignalHandler (Story 6.3).
     *
     * @param signalName Name of the signal being delivered
     * @param delivered True if signal delivered successfully, false otherwise
     */
    fun recordSignalDelivery(
        signalName: String,
        delivered: Boolean,
    ) {
        try {
            Counter
                .builder("flowable.signal.deliveries")
                .tag("signal_name", signalName)
                .tag("status", if (delivered) "success" else "failed")
                .description("Count of signal deliveries by name and success status")
                .register(meterRegistry)
                .increment()

            if (!delivered) {
                logger.warn("Signal delivery failed: signal_name={}", signalName)
            } else {
                logger.trace("Signal delivery recorded: signal_name={}, delivered={}", signalName, delivered)
            }
        } catch (ex: Exception) {
            logger.error("Failed to record signal delivery metric: signal_name={}", signalName, ex)
        }
    }

    /**
     * METRIC 6: Dead Letter Queue Depth (Subtask 1.6)
     *
     * Monitors Flowable dead letter job queue depth.
     * High queue depth indicates systematic processing failures requiring investigation.
     *
     * Scheduled execution: Every 60 seconds
     * **Alert Threshold:** > 100 jobs (critical - immediate action required)
     * **Rollback Trigger:** > 1000 jobs (investigate then rollback)
     */
    @Scheduled(fixedRate = 60000) // Every 60s
    fun recordDeadLetterQueue() {
        try {
            val management = processEngine.managementService
            val deadLetterJobs = management.createDeadLetterJobQuery().count()

            meterRegistry.gauge(
                "flowable.jobs.dead_letter",
                deadLetterJobs.toDouble(),
            )

            when {
                deadLetterJobs > 1000 -> {
                    logger.error(
                        "Dead letter queue depth CRITICAL: {} jobs (rollback threshold exceeded)",
                        deadLetterJobs,
                    )
                }

                deadLetterJobs > 100 -> {
                    logger.error(
                        "Dead letter queue depth HIGH: {} jobs (alert threshold exceeded)",
                        deadLetterJobs,
                    )
                }

                deadLetterJobs > 0 -> {
                    logger.warn("Dead letter queue depth: {} jobs", deadLetterJobs)
                }

                else -> {
                    logger.trace("Dead letter queue depth: 0 jobs (healthy)")
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed to record dead letter queue metrics", ex)
        }
    }
}
