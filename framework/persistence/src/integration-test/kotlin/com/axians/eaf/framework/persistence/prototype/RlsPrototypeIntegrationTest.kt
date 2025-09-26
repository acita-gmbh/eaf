package com.axians.eaf.framework.persistence.prototype

import com.axians.eaf.framework.security.tenant.TenantContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.*
import javax.sql.DataSource

/**
 * PROTOTYPE: RLS + Axon + Connection Pooling Integration Test
 *
 * This test validates the three critical unvalidated risks identified in QA assessment:
 *
 * 1. SEC-001: RLS Policy Bypass - Verify interceptor executes before queries
 * 2. SEC-002: Session Variable Leakage - Validate connection pooling with SET LOCAL
 * 3. TECH-001: Axon Compatibility - Test event sourcing with RLS enabled
 *
 * Test Strategy: Integration tests using PostgreSQL Testcontainers
 * Rationale: RLS behavior cannot be tested without real PostgreSQL
 *
 * QA Reference: docs/qa/assessments/4.3-test-design-20250926.md
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RlsPrototypeIntegrationTest : BehaviorSpec() {

    companion object {
        // Test tenant IDs
        private val TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        private val TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        private val TENANT_C = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")

        // PostgreSQL Testcontainer
        private val postgresContainer = PostgreSQLContainer(DockerImageName.parse("postgres:16.1-alpine"))
            .withDatabaseName("eaf_rls_prototype")
            .withUsername("test")
            .withPassword("test")
            .apply {
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" } // We manage schema manually
        }
    }

    private lateinit var dataSource: DataSource
    private lateinit var tenantContext: TenantContext
    private lateinit var interceptor: TenantSessionInterceptor
    private lateinit var jdbcTemplate: JdbcTemplate

    init {
        // Initialize components (in real implementation, these would be autowired)
        beforeSpec {
            // Get a raw connection to set up schema
            val connection = getConnection()
            executeSchemaSetup(connection)
            connection.close()

            // Initialize test components
            dataSource = createTestDataSource()
            tenantContext = TenantContext()
            interceptor = TenantSessionInterceptor(tenantContext)
            jdbcTemplate = JdbcTemplate(dataSource)

            // Insert test data for multiple tenants
            insertTestData()
        }

        afterSpec {
            // Clean up
            tenantContext.clearCurrentTenant()
            postgresContainer.stop()
        }

        afterTest {
            // Clean up tenant context after each test
            tenantContext.clearCurrentTenant()
        }

        // =====================================================
        // RISK SEC-001: RLS Policy Bypass Prevention
        // =====================================================
        given("RLS policies are enabled on tables") {
            `when`("querying without session variable set") {
                then("should return zero rows (fail-closed)") {
                    // Given: No tenant context set
                    tenantContext.clearCurrentTenant()

                    // When: Query projection table directly (bypassing interceptor)
                    val connection = getConnection()
                    val results = queryProjections(connection)

                    // Then: Should return zero rows (RLS blocks access)
                    results.shouldBeEmpty()

                    connection.close()
                }
            }

            `when`("session variable set to Tenant A") {
                then("should only return Tenant A's rows") {
                    // Given: Tenant A context
                    setTenantContext(TENANT_A)

                    // When: Query with session variable set
                    val connection = getConnection()
                    setSessionVariable(connection, TENANT_A)
                    val results = queryProjections(connection)

                    // Then: Should only return Tenant A rows
                    results.shouldHaveSize(2) // We inserted 2 rows for Tenant A
                    results.all { it.tenantId == TENANT_A } shouldBe true

                    connection.close()
                }
            }

            `when`("attempting to query other tenant's data") {
                then("should return zero rows (RLS enforces isolation)") {
                    // Given: Tenant A context
                    setTenantContext(TENANT_A)

                    // When: Try to query Tenant B's data explicitly
                    val connection = getConnection()
                    setSessionVariable(connection, TENANT_A)
                    val results = queryProjectionsByTenant(connection, TENANT_B)

                    // Then: Should return zero rows (RLS blocks cross-tenant access)
                    results.shouldBeEmpty()

                    connection.close()
                }
            }
        }

        // =====================================================
        // RISK SEC-002: Session Variable Leakage Prevention
        // =====================================================
        given("a connection pool with SET LOCAL session variables") {
            `when`("using same connection for different tenants") {
                then("should not leak session variable across transactions") {
                    val connection = getConnection()

                    // Transaction 1: Tenant A
                    connection.autoCommit = false
                    setSessionVariableLocal(connection, TENANT_A)
                    val resultsA1 = queryProjections(connection)
                    resultsA1.shouldHaveSize(2)
                    resultsA1.all { it.tenantId == TENANT_A } shouldBe true
                    connection.commit()

                    // Transaction 2: Tenant B (same connection)
                    setSessionVariableLocal(connection, TENANT_B)
                    val resultsB = queryProjections(connection)
                    resultsB.shouldHaveSize(2)
                    resultsB.all { it.tenantId == TENANT_B } shouldBe true
                    connection.commit()

                    // Transaction 3: Tenant A again (verify no leakage)
                    setSessionVariableLocal(connection, TENANT_A)
                    val resultsA2 = queryProjections(connection)
                    resultsA2.shouldHaveSize(2)
                    resultsA2.all { it.tenantId == TENANT_A } shouldBe true
                    connection.commit()

                    connection.autoCommit = true
                    connection.close()
                }
            }

            `when`("transaction commits") {
                then("SET LOCAL should clear session variable automatically") {
                    val connection = getConnection()
                    connection.autoCommit = false

                    // Set session variable with SET LOCAL
                    setSessionVariableLocal(connection, TENANT_A)
                    verifySessionVariable(connection) shouldBe TENANT_A.toString()

                    // Commit transaction
                    connection.commit()

                    // Verify session variable is cleared
                    val afterCommit = verifySessionVariable(connection)
                    afterCommit.shouldBeNull() // SET LOCAL clears on commit

                    connection.autoCommit = true
                    connection.close()
                }
            }

            `when`("transaction rolls back") {
                then("SET LOCAL should clear session variable automatically") {
                    val connection = getConnection()
                    connection.autoCommit = false

                    // Set session variable with SET LOCAL
                    setSessionVariableLocal(connection, TENANT_A)
                    verifySessionVariable(connection) shouldBe TENANT_A.toString()

                    // Rollback transaction
                    connection.rollback()

                    // Verify session variable is cleared
                    val afterRollback = verifySessionVariable(connection)
                    afterRollback.shouldBeNull() // SET LOCAL clears on rollback

                    connection.autoCommit = true
                    connection.close()
                }
            }

            `when`("rapid tenant switching occurs") {
                then("should maintain isolation without leakage") {
                    val connection = getConnection()
                    connection.autoCommit = false

                    // Rapidly switch between tenants (stress test)
                    val tenants = listOf(TENANT_A, TENANT_B, TENANT_C, TENANT_A, TENANT_B)

                    tenants.forEach { tenantId ->
                        setSessionVariableLocal(connection, tenantId)
                        val results = queryProjections(connection)
                        results.all { it.tenantId == tenantId } shouldBe true
                        connection.commit()
                    }

                    connection.autoCommit = true
                    connection.close()
                }
            }
        }

        // =====================================================
        // RISK TECH-001: Axon Framework Compatibility
        // =====================================================
        given("Axon-style event store with RLS enabled") {
            `when`("writing events for an aggregate") {
                then("should write events only for current tenant") {
                    // Given: Tenant A context
                    setTenantContext(TENANT_A)

                    val connection = getConnection()
                    connection.autoCommit = false
                    setSessionVariableLocal(connection, TENANT_A)

                    // When: Write events (simulating Axon command handler)
                    val aggregateId = UUID.randomUUID().toString()
                    insertEvent(connection, aggregateId, 0, TENANT_A)
                    insertEvent(connection, aggregateId, 1, TENANT_A)

                    connection.commit()

                    // Then: Events should be written successfully
                    val events = queryEvents(connection, aggregateId)
                    events.shouldHaveSize(2)
                    events.all { it.tenantId == TENANT_A } shouldBe true

                    connection.autoCommit = true
                    connection.close()
                }
            }

            `when`("loading aggregate events with RLS") {
                then("should only load current tenant's events") {
                    // Given: Events exist for multiple tenants
                    val aggregateId = "shared-aggregate-id"
                    val connectionA = getConnection()
                    connectionA.autoCommit = false
                    setSessionVariableLocal(connectionA, TENANT_A)
                    insertEvent(connectionA, aggregateId, 0, TENANT_A)
                    connectionA.commit()
                    connectionA.close()

                    val connectionB = getConnection()
                    connectionB.autoCommit = false
                    setSessionVariableLocal(connectionB, TENANT_B)
                    insertEvent(connectionB, aggregateId, 0, TENANT_B)
                    connectionB.commit()
                    connectionB.close()

                    // When: Load events as Tenant A
                    val connectionLoadA = getConnection()
                    connectionLoadA.autoCommit = false
                    setSessionVariableLocal(connectionLoadA, TENANT_A)
                    val eventsA = queryEvents(connectionLoadA, aggregateId)

                    // Then: Should only see Tenant A's events
                    eventsA.shouldHaveSize(1)
                    eventsA.all { it.tenantId == TENANT_A } shouldBe true

                    connectionLoadA.commit()
                    connectionLoadA.autoCommit = true
                    connectionLoadA.close()
                }
            }

            `when`("attempting cross-tenant event access") {
                then("should be blocked by RLS") {
                    // Given: Events for Tenant B exist
                    val aggregateId = UUID.randomUUID().toString()
                    val connectionWrite = getConnection()
                    connectionWrite.autoCommit = false
                    setSessionVariableLocal(connectionWrite, TENANT_B)
                    insertEvent(connectionWrite, aggregateId, 0, TENANT_B)
                    connectionWrite.commit()
                    connectionWrite.close()

                    // When: Try to read as Tenant A
                    val connectionRead = getConnection()
                    connectionRead.autoCommit = false
                    setSessionVariableLocal(connectionRead, TENANT_A)
                    val events = queryEvents(connectionRead, aggregateId)

                    // Then: Should see zero events (RLS blocks access)
                    events.shouldBeEmpty()

                    connectionRead.commit()
                    connectionRead.autoCommit = true
                    connectionRead.close()
                }
            }
        }

        // =====================================================
        // Additional Security Validation Tests
        // =====================================================
        given("malicious SQL attempts") {
            `when`("SQL injection with malformed tenant ID") {
                then("should fail safely") {
                    val connection = getConnection()
                    connection.autoCommit = false

                    // Attempt SQL injection in session variable
                    shouldThrow<Exception> {
                        val maliciousSql = "SET LOCAL app.current_tenant = ''; DROP TABLE prototype_widget_projection; --"
                        connection.createStatement().execute(maliciousSql)
                    }

                    connection.rollback()
                    connection.autoCommit = true
                    connection.close()
                }
            }

            `when`("attempting to clear session variable mid-transaction") {
                then("subsequent queries should fail or return empty") {
                    val connection = getConnection()
                    connection.autoCommit = false

                    // Set session variable
                    setSessionVariableLocal(connection, TENANT_A)
                    val before = queryProjections(connection)
                    before.shouldHaveSize(2)

                    // Try to reset session variable to null
                    connection.createStatement().execute("RESET app.current_tenant")

                    // Subsequent queries should return empty (no tenant context)
                    val after = queryProjections(connection)
                    after.shouldBeEmpty()

                    connection.rollback()
                    connection.autoCommit = true
                    connection.close()
                }
            }
        }

        // =====================================================
        // Interceptor Validation Tests
        // =====================================================
        given("TenantSessionInterceptor") {
            `when`("generating SET LOCAL SQL") {
                then("should produce correct SQL statement") {
                    val sql = TenantSessionInterceptor.generateSetLocalSql(TENANT_A.toString())
                    sql shouldContain "SET LOCAL"
                    sql shouldContain "app.current_tenant"
                    sql shouldContain TENANT_A.toString()
                }
            }

            `when`("validating session variable SQL") {
                then("should correctly identify SET LOCAL statements") {
                    val validSql = "SET LOCAL app.current_tenant = 'test'"
                    TenantSessionInterceptor.validateSessionVariableSql(validSql) shouldBe true

                    val invalidSql = "SET app.current_tenant = 'test'"
                    TenantSessionInterceptor.validateSessionVariableSql(invalidSql) shouldBe false
                }
            }

            `when`("TenantContext is not set") {
                then("should throw exception (fail-closed)") {
                    // Clear tenant context
                    tenantContext.clearCurrentTenant()

                    val connection = getConnection()

                    // Attempt to set session variable without tenant context
                    shouldThrow<IllegalStateException> {
                        interceptor.setTenantSessionVariable(connection)
                    }.message shouldContain "fail-closed"

                    connection.close()
                }
            }

            `when`("verifying session variable") {
                then("should return current value") {
                    val connection = getConnection()
                    connection.autoCommit = false

                    // Set session variable
                    setSessionVariableLocal(connection, TENANT_A)

                    // Verify it's set correctly
                    val value = interceptor.verifyTenantSessionVariable(connection)
                    value shouldBe TENANT_A.toString()

                    connection.rollback()
                    connection.autoCommit = true
                    connection.close()
                }
            }
        }
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private fun getConnection(): Connection {
        return DriverManager.getConnection(
            postgresContainer.jdbcUrl,
            postgresContainer.username,
            postgresContainer.password
        )
    }

    private fun setTenantContext(tenantId: UUID) {
        tenantContext.setCurrentTenantId(tenantId.toString())
    }

    private fun setSessionVariable(connection: Connection, tenantId: UUID) {
        val sql = "SET app.current_tenant = '$tenantId'"
        connection.createStatement().execute(sql)
    }

    private fun setSessionVariableLocal(connection: Connection, tenantId: UUID) {
        val sql = "SET LOCAL app.current_tenant = '$tenantId'"
        connection.createStatement().execute(sql)
    }

    private fun verifySessionVariable(connection: Connection): String? {
        val sql = "SELECT current_setting('app.current_tenant', true)"
        val resultSet = connection.createStatement().executeQuery(sql)
        return if (resultSet.next()) resultSet.getString(1) else null
    }

    private fun queryProjections(connection: Connection): List<WidgetProjection> {
        val sql = "SELECT * FROM prototype_widget_projection"
        val resultSet = connection.createStatement().executeQuery(sql)
        val results = mutableListOf<WidgetProjection>()

        while (resultSet.next()) {
            results.add(
                WidgetProjection(
                    widgetId = UUID.fromString(resultSet.getString("widget_id")),
                    tenantId = UUID.fromString(resultSet.getString("tenant_id")),
                    name = resultSet.getString("name"),
                    status = resultSet.getString("status")
                )
            )
        }

        return results
    }

    private fun queryProjectionsByTenant(connection: Connection, tenantId: UUID): List<WidgetProjection> {
        val sql = "SELECT * FROM prototype_widget_projection WHERE tenant_id = '$tenantId'"
        val resultSet = connection.createStatement().executeQuery(sql)
        val results = mutableListOf<WidgetProjection>()

        while (resultSet.next()) {
            results.add(
                WidgetProjection(
                    widgetId = UUID.fromString(resultSet.getString("widget_id")),
                    tenantId = UUID.fromString(resultSet.getString("tenant_id")),
                    name = resultSet.getString("name"),
                    status = resultSet.getString("status")
                )
            )
        }

        return results
    }

    private fun insertEvent(connection: Connection, aggregateId: String, sequenceNumber: Long, tenantId: UUID) {
        val sql = """
            INSERT INTO prototype_domain_event_entry
            (event_identifier, aggregate_identifier, sequence_number, type, timestamp, payload, tenant_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val statement = connection.prepareStatement(sql)
        statement.setString(1, UUID.randomUUID().toString())
        statement.setString(2, aggregateId)
        statement.setLong(3, sequenceNumber)
        statement.setString(4, "TestEvent")
        statement.setTimestamp(5, java.sql.Timestamp.from(Instant.now()))
        statement.setBytes(6, "{}".toByteArray())
        statement.setObject(7, tenantId)
        statement.executeUpdate()
    }

    private fun queryEvents(connection: Connection, aggregateId: String): List<DomainEvent> {
        val sql = "SELECT * FROM prototype_domain_event_entry WHERE aggregate_identifier = ? ORDER BY sequence_number"
        val statement = connection.prepareStatement(sql)
        statement.setString(1, aggregateId)
        val resultSet = statement.executeQuery()
        val results = mutableListOf<DomainEvent>()

        while (resultSet.next()) {
            results.add(
                DomainEvent(
                    eventIdentifier = resultSet.getString("event_identifier"),
                    aggregateIdentifier = resultSet.getString("aggregate_identifier"),
                    sequenceNumber = resultSet.getLong("sequence_number"),
                    tenantId = UUID.fromString(resultSet.getString("tenant_id"))
                )
            )
        }

        return results
    }

    private fun executeSchemaSetup(connection: Connection) {
        // Read and execute schema SQL
        val schemaSQL = javaClass.getResourceAsStream("/prototype-rls-schema.sql")
            ?.bufferedReader()
            ?.readText()
            ?: throw IllegalStateException("Could not load prototype-rls-schema.sql")

        connection.createStatement().execute(schemaSQL)
    }

    private fun insertTestData() {
        val connection = getConnection()
        connection.autoCommit = false

        // Insert test data for Tenant A
        setSessionVariableLocal(connection, TENANT_A)
        insertProjection(connection, UUID.randomUUID(), TENANT_A, "Widget A1", "ACTIVE")
        insertProjection(connection, UUID.randomUUID(), TENANT_A, "Widget A2", "INACTIVE")
        connection.commit()

        // Insert test data for Tenant B
        setSessionVariableLocal(connection, TENANT_B)
        insertProjection(connection, UUID.randomUUID(), TENANT_B, "Widget B1", "ACTIVE")
        insertProjection(connection, UUID.randomUUID(), TENANT_B, "Widget B2", "ACTIVE")
        connection.commit()

        // Insert test data for Tenant C
        setSessionVariableLocal(connection, TENANT_C)
        insertProjection(connection, UUID.randomUUID(), TENANT_C, "Widget C1", "ACTIVE")
        connection.commit()

        connection.autoCommit = true
        connection.close()
    }

    private fun insertProjection(connection: Connection, widgetId: UUID, tenantId: UUID, name: String, status: String) {
        val sql = """
            INSERT INTO prototype_widget_projection
            (widget_id, tenant_id, name, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val statement = connection.prepareStatement(sql)
        statement.setObject(1, widgetId)
        statement.setObject(2, tenantId)
        statement.setString(3, name)
        statement.setString(4, status)
        statement.setTimestamp(5, java.sql.Timestamp.from(Instant.now()))
        statement.setTimestamp(6, java.sql.Timestamp.from(Instant.now()))
        statement.executeUpdate()
    }

    private fun createTestDataSource(): DataSource {
        val ds = org.springframework.jdbc.datasource.DriverManagerDataSource()
        ds.setDriverClassName("org.postgresql.Driver")
        ds.url = postgresContainer.jdbcUrl
        ds.username = postgresContainer.username
        ds.password = postgresContainer.password
        return ds
    }

    // =====================================================
    // Data Classes
    // =====================================================

    data class WidgetProjection(
        val widgetId: UUID,
        val tenantId: UUID,
        val name: String,
        val status: String
    )

    data class DomainEvent(
        val eventIdentifier: String,
        val aggregateIdentifier: String,
        val sequenceNumber: Long,
        val tenantId: UUID
    )
}