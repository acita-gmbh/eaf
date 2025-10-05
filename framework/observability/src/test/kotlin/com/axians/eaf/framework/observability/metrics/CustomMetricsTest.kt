package com.axians.eaf.framework.observability.metrics

import com.axians.eaf.framework.security.tenant.TenantContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration

class CustomMetricsTest :
    FunSpec({
        val meterRegistry = SimpleMeterRegistry()
        val tenantContext = TenantContext(meterRegistry)
        val metrics = CustomMetrics(meterRegistry, tenantContext)

        afterEach {
            tenantContext.clearCurrentTenant()
            meterRegistry.clear()
        }

        test("5.2-UNIT-001: records command metrics with tenant tag") {
            tenantContext.setCurrentTenantId("tenant-metrics-test")

            metrics.recordCommand("CreateWidgetCommand", Duration.ofMillis(5), success = true)

            meterRegistry
                .counter(
                    "eaf.commands.total",
                    "type",
                    "CreateWidgetCommand",
                    "status",
                    "success",
                    "tenant_id",
                    "tenant-metrics-test",
                ).count() shouldBe 1.0

            meterRegistry
                .timer(
                    "eaf.commands.duration",
                    "type",
                    "CreateWidgetCommand",
                    "tenant_id",
                    "tenant-metrics-test",
                ).count() shouldBe 1L
        }

        test("5.2-UNIT-002: records event metrics when tenant context missing") {
            metrics.recordEvent("WidgetCreatedEvent", Duration.ofMillis(3), success = true)

            val counter =
                meterRegistry.counter(
                    "eaf.events.total",
                    "type",
                    "WidgetCreatedEvent",
                    "status",
                    "success",
                    "tenant_id",
                    "system",
                )

            counter.count() shouldBe 1.0
            meterRegistry
                .timer(
                    "eaf.events.duration",
                    "type",
                    "WidgetCreatedEvent",
                    "tenant_id",
                    "system",
                ).count() shouldBe 1L
        }
    })
