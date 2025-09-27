package com.axians.eaf.framework.widget.projections

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.framework.persistence.repositories.WidgetProjectionRepository
import com.axians.eaf.framework.security.tenant.TenantContext
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.TrackingEventProcessor
import org.axonframework.config.EventProcessingConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.util.UUID
import javax.sql.DataSource

/**
 * End-to-end integration test for Story 4.4: Tenant Context Propagation for Async Processors.
 *
 * **Test Scenarios**:
 * - 4.4-INT-007: Command → Event → Projection with RLS enabled
 * - 4.4-INT-009: Raw SQL bypasses RLS (negative validation)
 *
 * **Infrastructure**:
 * - PostgreSQL Testcontainer with RLS policies enabled
 * - Redis Testcontainer for rate limiting
 * - Full Axon Framework (command bus, event store, tracking processors)
 * - Spring Boot application context
 *
 * **Critical Validation** (AC4):
 * Verifies that widget_projection table is successfully written to (passing RLS check)
 * and data is correct after async event processing.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "axon.axonserver.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class TenantContextPropagationE2EIntegrationTest(
    private val commandGateway: CommandGateway,
    private val eventBus: EventBus,
    private val eventProcessingConfiguration: EventProcessingConfiguration,
    private val repository: WidgetProjectionRepository,
    private val tenantContext: TenantContext,
    private val dataSource: DataSource,
) : BehaviorSpec() {

    companion object {
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("eaf_test")
            .withUsername("test")
            .withPassword("test")
            .apply { start() }

        private val redis = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
        }
    }

    init {

        beforeSpec {
            // Initialize RLS schema
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    val schema = this::class.java.getResource("/prototype-rls-schema.sql")?.readText()
                        ?: throw IllegalStateException("RLS schema not found")
                    stmt.execute(schema)
                }
            }

            // Start tracking event processor
            eventProcessingConfiguration.eventProcessors()
                .filter { it.value is TrackingEventProcessor }
                .forEach { (name, processor) ->
                    (processor as TrackingEventProcessor).start()
                }
        }

        afterSpec {
            postgres.stop()
            redis.stop()
        }

        afterTest {
            tenantContext.clearCurrentTenant()
        }

        Given("Full CQRS stack with RLS-enabled PostgreSQL") {

        When("command is dispatched for tenant-a") {
            val widgetId = UUID.randomUUID().toString()
            tenantContext.setCurrentTenantId("tenant-a")

            val command = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = "tenant-a",
                name = "Test Widget",
                description = "E2E test",
                value = BigDecimal("99.99"),
                category = "TEST",
                metadata = emptyMap(),
            )

            commandGateway.sendAndWait<Any>(command)

            // Wait for async projection processing
            Thread.sleep(1000)

            Then("4.4-INT-007: widget_projection table contains row for tenant-a (RLS passed)") {
                tenantContext.setCurrentTenantId("tenant-a")

                dataSource.connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("SET LOCAL app.current_tenant = 'tenant-a'")
                    }
                }

                val projection = repository.findByWidgetIdAndTenantId(widgetId, "tenant-a")

                projection shouldNotBe null
                projection?.name shouldBe "Test Widget"
                projection?.widgetId shouldBe widgetId
            }
        }

        When("raw SQL is executed without session variable") {
            tenantContext.clearCurrentTenant()

            Then("4.4-INT-009: RLS blocks access (zero rows returned)") {
                dataSource.connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM prototype_widget_projection WHERE tenant_id = 'tenant-a'::UUID",
                        )
                        rs.next()
                        val count = rs.getInt(1)

                        count shouldBe 0
                    }
                }
            }
        }
        }
    }
}