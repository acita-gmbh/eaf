package com.axians.eaf.framework.workflow.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import org.flowable.engine.ProcessEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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

    // QA Fix (MEDIUM-01): Mutable state for Gauge observation (prevents memory leak)
    // Gauges are registered ONCE during initialization and observe these state holders
    private val activeInstancesByProcessKey = ConcurrentHashMap<String, AtomicLong>()
    private val totalActiveInstances = AtomicLong(0)
    private val suspendedInstances = AtomicLong(0)
    private val deadLetterJobs = AtomicLong(0)

    /**
     * Register Gauges ONCE during component initialization (QA Fix MEDIUM-01).
     *
     * Gauges observe mutable state via lambda functions. The @Scheduled methods
     * update the state, and gauges automatically reflect the current values.
     *
     * **Fix Rationale**: Previous implementation re-registered gauges on every
     * @Scheduled invocation (30s/60s), causing ~4,320 new instances per day and
     * eventual OOM. This pattern registers once and observes state.
     */
    @PostConstruct
    fun registerGauges() {
        // Total active instances gauge (observes AtomicLong)
        Gauge
            .builder("flowable.process.instances.active.total", totalActiveInstances) { it.get().toDouble() }
            .description("Total number of active process instances across all definitions")
            .register(meterRegistry)

        // Suspended instances gauge (observes AtomicLong)
        Gauge
            .builder("flowable.process.instances.suspended", suspendedInstances) { it.get().toDouble() }
            .description("Number of suspended process instances")
            .register(meterRegistry)

        // Dead letter queue depth gauge (observes AtomicLong)
        Gauge
            .builder("flowable.jobs.dead_letter", deadLetterJobs) { it.get().toDouble() }
            .description("Number of jobs in the dead letter queue")
            .register(meterRegistry)

        logger.info("Flowable metrics gauges registered (total: 3 base gauges + dynamic per-process-key)")
    }

    /**
     * METRIC 1 & 2: Active and Suspended Process Instances (Subtasks 1.2)
     *
     * Updates mutable state for active/suspended process instances.
     * Gauges registered in @PostConstruct observe this state automatically.
     *
     * **QA Fix (MEDIUM-01)**: Refactored to UPDATE state instead of re-registering
     * gauges. Prevents memory leak from duplicate gauge instances.
     *
     * Scheduled execution: Every 30 seconds
     * Alert threshold: Suspended instances > 0 (investigate immediately)
     */
    @Scheduled(fixedRate = 30000) // Every 30s
    fun recordProcessInstanceMetrics() {
        try {
            val runtime = processEngine.runtimeService

            // Query active instances
            val activeInstances =
                runtime
                    .createProcessInstanceQuery()
                    .active()
                    .list()

            // Update total active instances state
            totalActiveInstances.set(activeInstances.size.toLong())

            // Update per-process-key active instances
            val currentCounts = activeInstances.groupingBy { it.processDefinitionKey ?: "unknown" }.eachCount()

            // Clear old keys and update current counts
            activeInstancesByProcessKey.clear()
            currentCounts.forEach { (processKey, count) ->
                val atomicCount =
                    activeInstancesByProcessKey.computeIfAbsent(processKey) {
                        // First time seeing this process key - register a new gauge for it
                        val newGauge = AtomicLong(0)
                        Gauge
                            .builder("flowable.process.instances.active", newGauge) { it.get().toDouble() }
                            .tag("process_key", processKey)
                            .description("Number of active process instances by process definition")
                            .register(meterRegistry)
                        newGauge
                    }
                atomicCount.set(count.toLong())
            }

            // Update suspended instances state
            val suspendedCount = runtime.createProcessInstanceQuery().suspended().count()
            suspendedInstances.set(suspendedCount)

            if (suspendedCount > 0) {
                logger.warn("Suspended process instances detected: {} instances", suspendedCount)
            }

            logger.trace(
                "Process instance metrics updated: {} active, {} suspended",
                activeInstances.size,
                suspendedCount,
            )
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Infrastructure Interceptor Exception Pattern:
            // Observability component gracefully degrades when metrics recording fails
            // (e.g., Prometheus unavailable). Never crash application due to metrics failure.
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
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Infrastructure Interceptor Exception Pattern:
            // Observability component gracefully degrades when metrics recording fails.
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
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Infrastructure Interceptor Exception Pattern:
            // Observability component gracefully degrades when metrics recording fails.
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
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Infrastructure Interceptor Exception Pattern:
            // Observability component gracefully degrades when metrics recording fails.
            logger.error("Failed to record signal delivery metric: signal_name={}", signalName, ex)
        }
    }

    /**
     * METRIC 6: Dead Letter Queue Depth (Subtask 1.6)
     *
     * Updates mutable state for dead letter queue depth.
     * Gauge registered in @PostConstruct observes this state automatically.
     *
     * **QA Fix (MEDIUM-01)**: Refactored to UPDATE state instead of calling
     * meterRegistry.gauge() repeatedly.
     *
     * Scheduled execution: Every 60 seconds
     * **Alert Threshold:** > 100 jobs (critical - immediate action required)
     * **Rollback Trigger:** > 1000 jobs (investigate then rollback)
     */
    @Scheduled(fixedRate = 60000) // Every 60s
    fun recordDeadLetterQueue() {
        try {
            val management = processEngine.managementService
            val count = management.createDeadLetterJobQuery().count()

            // Update state (gauge observes this AtomicLong)
            deadLetterJobs.set(count)

            when {
                count > 1000 -> {
                    logger.error(
                        "Dead letter queue depth CRITICAL: {} jobs (rollback threshold exceeded)",
                        count,
                    )
                }

                count > 100 -> {
                    logger.error(
                        "Dead letter queue depth HIGH: {} jobs (alert threshold exceeded)",
                        count,
                    )
                }

                count > 0 -> {
                    logger.warn("Dead letter queue depth: {} jobs", count)
                }

                else -> {
                    logger.trace("Dead letter queue depth: 0 jobs (healthy)")
                }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Infrastructure Interceptor Exception Pattern:
            // Observability component gracefully degrades when metrics recording fails.
            logger.error("Failed to record dead letter queue metrics", ex)
        }
    }
}
