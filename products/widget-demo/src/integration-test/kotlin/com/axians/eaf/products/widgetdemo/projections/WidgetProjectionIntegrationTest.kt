package com.axians.eaf.products.widgetdemo.projections

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.framework.persistence.repositories.WidgetProjectionRepository
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class WidgetProjectionIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var repository: WidgetProjectionRepository

    init {
        extension(SpringExtension())

        beforeTest {
            repository.deleteAll()
        }

        context("Widget Projection Handler Tests") {
            test("should create projection from WidgetCreatedEvent") {
                val widgetId = UUID.randomUUID().toString()
                val tenantId = "projection-test-001"

                val command =
                    CreateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        name = "Projection Test Widget",
                        description = "Testing projection creation",
                        value = BigDecimal("350.50"),
                        category = "PROJECTION_TEST",
                        metadata = mapOf("source" to "projection-test"),
                    )

                commandGateway.sendAndWait<String>(command, 5, TimeUnit.SECONDS)

                // Allow time for event processing
                Thread.sleep(1000)

                val projection = repository.findByWidgetIdAndTenantId(widgetId, tenantId)
                projection shouldNotBe null
                projection!!.widgetId shouldBe widgetId
                projection.getTenantId() shouldBe tenantId
                projection.name shouldBe "Projection Test Widget"
                projection.description shouldBe "Testing projection creation"
                projection.value shouldBe BigDecimal("350.50")
                projection.category shouldBe "PROJECTION_TEST"
            }

            test("should handle multiple projections with tenant isolation") {
                val tenant1 = "projection-tenant-001"
                val tenant2 = "projection-tenant-002"

                val command1 =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenant1,
                        name = "Tenant 1 Widget",
                        description = null,
                        value = BigDecimal("100.00"),
                        category = "ISOLATION_TEST",
                        metadata = emptyMap(),
                    )

                val command2 =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenant2,
                        name = "Tenant 2 Widget",
                        description = null,
                        value = BigDecimal("200.00"),
                        category = "ISOLATION_TEST",
                        metadata = emptyMap(),
                    )

                commandGateway.sendAndWait<String>(command1, 5, TimeUnit.SECONDS)
                commandGateway.sendAndWait<String>(command2, 5, TimeUnit.SECONDS)

                Thread.sleep(1500)

                val tenant1Projections = repository.findByTenantIdOrderByCreatedAtDesc(tenant1)
                val tenant2Projections = repository.findByTenantIdOrderByCreatedAtDesc(tenant2)

                tenant1Projections.size shouldBe 1
                tenant2Projections.size shouldBe 1

                tenant1Projections.all { it.getTenantId() == tenant1 } shouldBe true
                tenant2Projections.all { it.getTenantId() == tenant2 } shouldBe true
            }
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.startAll()

            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }
        }
    }
}
