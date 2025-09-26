package com.axians.eaf.framework.widget.api

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.commands.UpdateWidgetCommand
import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.framework.widget.test.WidgetFrameworkTestApplication
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest(
    classes = [WidgetFrameworkTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "axon.axonserver.enabled=false",
    ],
)
@ActiveProfiles("test")
class TenantBoundaryValidationIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())

        afterTest {
            tenantContext.clearCurrentTenant()
        }

        test("Tenant A cannot update Tenant B's widget") {
            val widgetId = UUID.randomUUID().toString()

            tenantContext.setCurrentTenantId("tenant-b")
            commandGateway.sendAndWait<String>(
                CreateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "tenant-b",
                    name = "Tenant B Widget",
                    description = "Owned by Tenant B",
                    value = BigDecimal("100.00"),
                    category = "TEST_CATEGORY",
                    metadata = emptyMap(),
                ),
            )
            tenantContext.clearCurrentTenant()

            tenantContext.setCurrentTenantId("tenant-a")

            shouldThrow<Exception> {
                commandGateway.sendAndWait<Unit>(
                    UpdateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = "tenant-a",
                        name = "Hacked Widget",
                    ),
                )
            }.message shouldContain "Tenant isolation violation"
        }

        test("Tenant A cannot create widget with Tenant B's tenantId") {
            val widgetId = UUID.randomUUID().toString()
            tenantContext.setCurrentTenantId("tenant-a")

            shouldThrow<IllegalArgumentException> {
                commandGateway.sendAndWait<String>(
                    CreateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = "tenant-b",
                        name = "Spoofed Widget",
                        description = "Attempting tenant spoofing",
                        value = BigDecimal("50.00"),
                        category = "ATTACK_CAT",
                        metadata = emptyMap(),
                    ),
                )
            }.message shouldContain "Tenant isolation violation"
        }
    }
}
