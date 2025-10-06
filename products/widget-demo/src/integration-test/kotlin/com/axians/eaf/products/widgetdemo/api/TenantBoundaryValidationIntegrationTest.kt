package com.axians.eaf.products.widgetdemo.api

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.commands.UpdateWidgetCommand
import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.products.widgetdemo.domain.Widget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.FixtureConfiguration
import java.math.BigDecimal
import java.util.UUID

class TenantBoundaryValidationIntegrationTest : FunSpec() {
    private lateinit var fixture: FixtureConfiguration<Widget>
    private val tenantContext = TenantContext(SimpleMeterRegistry())

    init {
        beforeEach {
            fixture = AggregateTestFixture(Widget::class.java)
        }

        afterEach {
            tenantContext.clearCurrentTenant()
        }

        test("4.2-INT-001: Tenant A cannot update Tenant B's widget (cross-tenant attack prevention)") {
            val widgetId = UUID.randomUUID().toString()

            tenantContext.setCurrentTenantId("tenant-b")
            val createCommand =
                CreateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "tenant-b",
                    name = "Tenant B Widget",
                    description = "Owned by Tenant B",
                    value = BigDecimal("100.00"),
                    category = "TEST_CATEGORY",
                    metadata = emptyMap(),
                )

            fixture
                .givenNoPriorActivity()
                .`when`(createCommand)
                .expectSuccessfulHandlerExecution()

            tenantContext.clearCurrentTenant()
            tenantContext.setCurrentTenantId("tenant-a")

            val updateCommand =
                UpdateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "tenant-a",
                    name = "Hacked Widget",
                )

            fixture
                .given(
                    WidgetCreatedEvent(
                        widgetId = widgetId,
                        tenantId = "tenant-b",
                        name = "Tenant B Widget",
                        description = "Owned by Tenant B",
                        value = BigDecimal("100.00"),
                        category = "TEST_CATEGORY",
                        metadata = emptyMap(),
                    ),
                ).`when`(updateCommand)
                .expectNoEvents()
                .expectSuccessfulHandlerExecution()
        }

        test("4.2-INT-002: Tenant A cannot create widget with Tenant B's tenantId (tenant spoofing prevention)") {
            val widgetId = UUID.randomUUID().toString()
            tenantContext.setCurrentTenantId("tenant-a")

            val command =
                CreateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "tenant-b",
                    name = "Spoofed Widget",
                    description = "Attempting tenant spoofing",
                    value = BigDecimal("50.00"),
                    category = "ATTACK_CAT",
                    metadata = emptyMap(),
                )

            fixture
                .givenNoPriorActivity()
                .`when`(command)
                .expectException(IllegalArgumentException::class.java)
        }

        test("4.2-INT-003: Transaction rollback verification - no events after tenant violation") {
            val widgetId = UUID.randomUUID().toString()
            tenantContext.setCurrentTenantId("tenant-a")

            val command =
                CreateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "tenant-b",
                    name = "Invalid Widget",
                    description = null,
                    value = BigDecimal("100.00"),
                    category = "TEST_CATEGORY",
                    metadata = emptyMap(),
                )

            fixture
                .givenNoPriorActivity()
                .`when`(command)
                .expectException(IllegalArgumentException::class.java)
                .expectNoEvents()
        }
    }
}
