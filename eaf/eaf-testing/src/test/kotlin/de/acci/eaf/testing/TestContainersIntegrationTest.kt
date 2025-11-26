package de.acci.eaf.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.DriverManager

/**
 * Integration tests for TestContainers setup and @IsolatedEventStore isolation behavior.
 *
 * **Test Groups:**
 * - Tests 1-2: Container startup and Keycloak realm verification
 * - Tests 10-11: Event store isolation verification (order-dependent)
 *
 * **Note on @Order annotations for isolation tests:**
 * Tests 10 and 11 intentionally use execution ordering to verify the isolation mechanism:
 * - Test 10 inserts data into the event store
 * - Test 11 verifies that @IsolatedEventStore(TRUNCATE) cleared the data before it ran
 *
 * This ordering is required to validate that the isolation annotation works correctly.
 * Do not refactor to remove @Order without providing an alternative isolation verification.
 */
@IsolatedEventStore(strategy = IsolationStrategy.TRUNCATE)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestContainersIntegrationTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setupSchema() {
            // Ensure the event store schema is created (uses the shared idempotent setup)
            // This loads the migration SQL from the classpath resource
            val migrationSql = TestContainersIntegrationTest::class.java
                .getResource("/db/migration/V001__create_event_store.sql")
                ?.readText()

            if (migrationSql != null) {
                // Use the idempotent schema setup that drops and recreates
                TestContainers.ensureEventStoreSchema { migrationSql }
            } else {
                // Fallback: create a minimal schema for testing isolation (only if not exists)
                val url = TestContainers.postgres.jdbcUrl
                val user = TestContainers.postgres.username
                val password = TestContainers.postgres.password

                DriverManager.getConnection(url, user, password).use { conn ->
                    // Check if schema already exists (e.g., created by eaf-eventsourcing)
                    val schemaExists = conn.createStatement().use { stmt ->
                        stmt.executeQuery(
                            "SELECT 1 FROM information_schema.schemata WHERE schema_name = 'eaf_events'"
                        ).use { rs -> rs.next() }
                    }

                    if (!schemaExists) {
                        // Only create minimal schema if nothing exists
                        conn.createStatement().use { stmt ->
                            stmt.execute("CREATE SCHEMA eaf_events")
                        }
                        conn.createStatement().use { stmt ->
                            stmt.execute(
                                """
                                CREATE TABLE eaf_events.events (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    aggregate_id UUID NOT NULL,
                                    aggregate_type VARCHAR(255) NOT NULL,
                                    event_type VARCHAR(255) NOT NULL,
                                    payload JSONB NOT NULL,
                                    metadata JSONB NOT NULL DEFAULT '{}',
                                    tenant_id UUID NOT NULL,
                                    version INT NOT NULL,
                                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                    CONSTRAINT uq_aggregate_version UNIQUE (tenant_id, aggregate_id, version),
                                    CONSTRAINT chk_version_positive CHECK (version > 0)
                                )
                                """.trimIndent()
                            )
                        }
                        conn.createStatement().use { stmt ->
                            stmt.execute("CREATE INDEX idx_events_tenant ON eaf_events.events (tenant_id)")
                        }
                        conn.createStatement().use { stmt ->
                            stmt.execute(
                                "CREATE TABLE eaf_events.snapshots (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), state JSONB)"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    @Order(1)
    fun `containers are running`() {
        assertTrue(TestContainers.postgres.isRunning)
        assertTrue(TestContainers.keycloak.isRunning)
    }

    @Test
    @Order(2)
    fun `keycloak realm dvmm is imported with test user`() {
        // Verify the test realm was imported correctly by checking OpenID configuration
        val keycloak = TestContainers.keycloak
        val realmUrl = "${keycloak.authServerUrl}/realms/dvmm"

        val httpClient = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$realmUrl/.well-known/openid-configuration"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        // Realm exists if OpenID configuration is accessible
        assertEquals(200, response.statusCode(), "Realm 'dvmm' should be accessible")
        assertNotNull(response.body())
        assertTrue(response.body().contains("\"issuer\""), "Response should contain OpenID issuer")
        assertTrue(response.body().contains("dvmm"), "Issuer should reference 'dvmm' realm")
    }

    @Test
    @Order(10)
    fun `isolation test - insert data for subsequent truncation verification`() {
        // Insert test data that should be cleared by @IsolatedEventStore before the next test
        val url = TestContainers.postgres.jdbcUrl
        val user = TestContainers.postgres.username
        val password = TestContainers.postgres.password

        DriverManager.getConnection(url, user, password).use { conn ->
            conn.createStatement().use { stmt ->
                // Use valid JSON for JSONB column (schema may be upgraded by other tests)
                stmt.execute(
                    """
                    INSERT INTO eaf_events.events
                        (aggregate_id, aggregate_type, event_type, payload, metadata, tenant_id, version)
                    VALUES (
                        gen_random_uuid(),
                        'TestAggregate',
                        'TestEvent',
                        '{"test": "data"}'::jsonb,
                        '{"tenantId": "00000000-0000-0000-0000-000000000000"}'::jsonb,
                        '00000000-0000-0000-0000-000000000000',
                        1
                    )
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    @Order(11)
    fun `isolation test - verify IsolatedEventStore truncated previous test data`() {
        // Validates @IsolatedEventStore(TRUNCATE) works: data from test 2 should be cleared
        val url = TestContainers.postgres.jdbcUrl
        val user = TestContainers.postgres.username
        val password = TestContainers.postgres.password

        DriverManager.getConnection(url, user, password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM eaf_events.events").use { rs ->
                    rs.next()
                    val count = rs.getInt(1)
                    assertEquals(0, count, "Event store should be empty due to isolation")
                }
            }
        }
    }
}
