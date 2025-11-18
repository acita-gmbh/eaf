package com.axians.eaf.products.widget.api

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.products.widget.domain.CreateWidgetCommand
import com.axians.eaf.products.widget.domain.WidgetId
import com.axians.eaf.products.widget.query.FindWidgetQuery
import com.axians.eaf.products.widget.query.WidgetProjection
import com.axians.eaf.shared.testing.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.extension.spring.SpringExtension
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Multi-tenant integration test for Widget aggregate (Story 4.6).
 *
 * Verifies:
 * - AC6: Integration test creates widgets for multiple tenants
 * - AC7: Cross-tenant access test validates isolation (tenant A cannot see tenant B widgets)
 *
 * Tests the complete multi-tenancy stack:
 * - Layer 2: Command handler tenant validation
 * - Layer 3: PostgreSQL RLS policies
 * - Event metadata: tenant_id propagation to projections
 */
@SpringBootTest
@ActiveProfiles("test")
@IntegrationTest
class WidgetMultiTenantIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var queryGateway: QueryGateway

    override fun extensions(): List<Extension> = listOf(SpringExtension)

    init {
        test("AC6: Create widgets for multiple tenants successfully") {
            val tenantAId = "tenant-a"
            val tenantBId = "tenant-b"

            val widgetATenantA = WidgetId(UUID.randomUUID())
            val widgetBTenantB = WidgetId(UUID.randomUUID())

            // Create widget for tenant-a
            TenantContext.setCurrentTenantId(tenantAId)
            try {
                commandGateway.sendAndWait<Any>(
                    CreateWidgetCommand(widgetATenantA, "Widget for Tenant A", tenantAId),
                    10,
                    TimeUnit.SECONDS,
                )
            } finally {
                TenantContext.clearCurrentTenant()
            }

            // Create widget for tenant-b
            TenantContext.setCurrentTenantId(tenantBId)
            try {
                commandGateway.sendAndWait<Any>(
                    CreateWidgetCommand(widgetBTenantB, "Widget for Tenant B", tenantBId),
                    10,
                    TimeUnit.SECONDS,
                )
            } finally {
                TenantContext.clearCurrentTenant()
            }

            // Verify tenant-a can see their widget
            TenantContext.setCurrentTenantId(tenantAId)
            try {
                val projectionA =
                    queryGateway
                        .query(
                            FindWidgetQuery(widgetATenantA),
                            ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                        ).get()
                        .orElse(null)

                projectionA shouldNotBe null
                projectionA?.name shouldBe "Widget for Tenant A"
            } finally {
                TenantContext.clearCurrentTenant()
            }

            // Verify tenant-b can see their widget
            TenantContext.setCurrentTenantId(tenantBId)
            try {
                val projectionB =
                    queryGateway
                        .query(
                            FindWidgetQuery(widgetBTenantB),
                            ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                        ).get()
                        .orElse(null)

                projectionB shouldNotBe null
                projectionB?.name shouldBe "Widget for Tenant B"
            } finally {
                TenantContext.clearCurrentTenant()
            }
        }

        test("AC7: Cross-tenant isolation - tenant A cannot see tenant B widgets") {
            val tenantAId = "tenant-a-isolation"
            val tenantBId = "tenant-b-isolation"

            val widgetIdForTenantB = WidgetId(UUID.randomUUID())

            // Create widget for tenant-b
            TenantContext.setCurrentTenantId(tenantBId)
            try {
                commandGateway.sendAndWait<Any>(
                    CreateWidgetCommand(widgetIdForTenantB, "Tenant B Private Widget", tenantBId),
                    10,
                    TimeUnit.SECONDS,
                )
            } finally {
                TenantContext.clearCurrentTenant()
            }

            // Wait for projection (eventual consistency)
            Thread.sleep(500)

            // Verify tenant-a CANNOT see tenant-b's widget (PostgreSQL RLS blocks it)
            TenantContext.setCurrentTenantId(tenantAId)
            try {
                val projection =
                    queryGateway
                        .query(
                            FindWidgetQuery(widgetIdForTenantB),
                            ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                        ).get()
                        .orElse(null)

                // RLS policy should prevent tenant-a from seeing tenant-b's data
                projection.shouldBeNull()
            } finally {
                TenantContext.clearCurrentTenant()
            }
        }

        test("AC3: Command with mismatched tenant context is rejected") {
            val tenantAId = "tenant-a-mismatch"
            val tenantBId = "tenant-b-mismatch"

            val widgetId = WidgetId(UUID.randomUUID())

            // Set context for tenant-a, but try to create widget for tenant-b
            TenantContext.setCurrentTenantId(tenantAId)
            try {
                val exception =
                    shouldThrow<Exception> {
                        commandGateway.sendAndWait<Any>(
                            CreateWidgetCommand(widgetId, "Malicious Widget", tenantBId),
                            10,
                            TimeUnit.SECONDS,
                        )
                    }

                // Verify the exception message is generic (CWE-209 protection)
                exception.message shouldContain "tenant context mismatch"
            } finally {
                TenantContext.clearCurrentTenant()
            }
        }
    }
}
