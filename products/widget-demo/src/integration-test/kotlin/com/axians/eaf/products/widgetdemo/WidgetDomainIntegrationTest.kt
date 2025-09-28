package com.axians.eaf.products.widgetdemo

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.commands.UpdateWidgetCommand
import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.api.widget.events.WidgetUpdatedEvent
import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.products.widgetdemo.domain.Widget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.FixtureConfiguration
import java.math.BigDecimal
import java.util.UUID

/**
 * Domain integration tests for Widget aggregate using AggregateTestFixture.
 * Validates command handling, event sourcing, and tenant isolation at domain level.
 * Uses pure Axon test fixtures (no Spring Boot context).
 */
class WidgetDomainIntegrationTest : FunSpec() {
    private lateinit var fixture: FixtureConfiguration<Widget>
    private val tenantContext = TenantContext(SimpleMeterRegistry())

    init {
        beforeEach {
            fixture = AggregateTestFixture(Widget::class.java)
        }

        afterEach {
            tenantContext.clearCurrentTenant()
        }

        test("should create widget with valid command") {
            val widgetId = UUID.randomUUID().toString()
            tenantContext.setCurrentTenantId("tenant-a")

            val createCommand = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = "tenant-a",
                name = "Domain Test Widget",
                description = "Created via domain integration test",
                value = BigDecimal("100.00"),
                category = "DOMAIN_TEST",
                metadata = emptyMap()
            )

            fixture
                .givenNoPriorActivity()
                .`when`(createCommand)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                    WidgetCreatedEvent(
                        widgetId = widgetId,
                        tenantId = "tenant-a",
                        name = "Domain Test Widget",
                        description = "Created via domain integration test",
                        value = BigDecimal("100.00"),
                        category = "DOMAIN_TEST",
                        metadata = emptyMap()
                    )
                )
        }

        test("should update existing widget") {
            val widgetId = UUID.randomUUID().toString()
            tenantContext.setCurrentTenantId("tenant-a")

            val updateCommand = UpdateWidgetCommand(
                widgetId = widgetId,
                tenantId = "tenant-a",
                name = "Updated Domain Widget",
                value = BigDecimal("200.00")
            )

            fixture
                .given(
                    WidgetCreatedEvent(
                        widgetId = widgetId,
                        tenantId = "tenant-a",
                        name = "Original Widget",
                        description = "Original description",
                        value = BigDecimal("100.00"),
                        category = "DOMAIN_TEST",
                        metadata = emptyMap()
                    )
                )
                .`when`(updateCommand)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                    WidgetUpdatedEvent(
                        widgetId = widgetId,
                        tenantId = "tenant-a",
                        name = "Updated Domain Widget",
                        description = null,
                        value = BigDecimal("200.00"),
                        category = null,
                        metadata = null
                    )
                )
        }

        test("should reject create with tenant mismatch") {
            val widgetId = UUID.randomUUID().toString()
            tenantContext.setCurrentTenantId("tenant-a")

            val createCommand = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = "tenant-b",  // Mismatch!
                name = "Malicious Widget",
                description = null,
                value = BigDecimal("100.00"),
                category = "ATTACK",
                metadata = emptyMap()
            )

            fixture
                .givenNoPriorActivity()
                .`when`(createCommand)
                .expectException(IllegalArgumentException::class.java)
                .expectNoEvents()
        }

        test("should reject update with tenant mismatch") {
            val widgetId = UUID.randomUUID().toString()
            tenantContext.setCurrentTenantId("tenant-b")

            val updateCommand = UpdateWidgetCommand(
                widgetId = widgetId,
                tenantId = "tenant-b",
                name = "Cross-tenant Attack"
            )

            fixture
                .given(
                    WidgetCreatedEvent(
                        widgetId = widgetId,
                        tenantId = "tenant-a",  // Different tenant
                        name = "Tenant A Widget",
                        description = null,
                        value = BigDecimal("100.00"),
                        category = "TEST",
                        metadata = emptyMap()
                    )
                )
                .`when`(updateCommand)
                .expectNoEvents()
                .expectSuccessfulHandlerExecution()
        }
    }
}