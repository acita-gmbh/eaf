package com.axians.eaf.framework.observability.metrics

import com.axians.eaf.framework.security.tenant.TenantContext
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration

/**
 * Provides standardized counters and timers for command and event processing metrics.
 *
 * Metrics emitted:
 * - `eaf.commands.total` (tags: type, status, tenant_id)
 * - `eaf.commands.duration` (tags: type, tenant_id)
 * - `eaf.events.total` (tags: type, status, tenant_id)
 * - `eaf.events.duration` (tags: type, tenant_id)
 */
class CustomMetrics(
    private val meterRegistry: MeterRegistry,
    private val tenantContext: TenantContext,
) {
    fun recordCommand(
        commandType: String,
        duration: Duration,
        success: Boolean,
    ) {
        val tenantTag = currentTenant()

        meterRegistry
            .counter(
                "eaf.commands.total",
                "type",
                commandType,
                "status",
                if (success) "success" else "error",
                "tenant_id",
                tenantTag,
            ).increment()

        meterRegistry
            .timer(
                "eaf.commands.duration",
                "type",
                commandType,
                "tenant_id",
                tenantTag,
            ).record(duration)
    }

    fun recordEvent(
        eventType: String,
        duration: Duration,
        success: Boolean,
    ) {
        val tenantTag = currentTenant()

        meterRegistry
            .counter(
                "eaf.events.total",
                "type",
                eventType,
                "status",
                if (success) "success" else "error",
                "tenant_id",
                tenantTag,
            ).increment()

        meterRegistry
            .timer(
                "eaf.events.duration",
                "type",
                eventType,
                "tenant_id",
                tenantTag,
            ).record(duration)
    }

    private fun currentTenant(): String = tenantContext.current() ?: "system"
}
