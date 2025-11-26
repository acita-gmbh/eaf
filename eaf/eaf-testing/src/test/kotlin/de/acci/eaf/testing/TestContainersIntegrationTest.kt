package de.acci.eaf.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.sql.DriverManager

/**
 * Integration tests for TestContainers setup and @IsolatedEventStore isolation behavior.
 *
 * **Note on @Order annotations:**
 * Tests 2 and 3 intentionally use execution ordering to verify the isolation mechanism:
 * - Test 2 inserts data into the event store
 * - Test 3 verifies that @IsolatedEventStore(TRUNCATE) cleared the data before it ran
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
            // Ensure containers are started
            val url = TestContainers.postgres.jdbcUrl
            val user = TestContainers.postgres.username
            val password = TestContainers.postgres.password

            DriverManager.getConnection(url, user, password).use { conn ->
                conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS eaf_events")
                conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS eaf_events.events (id SERIAL PRIMARY KEY, payload TEXT)",
                )
                conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS eaf_events.snapshots (id SERIAL PRIMARY KEY, state TEXT)",
                )
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
    fun `isolation test - insert data for subsequent truncation verification`() {
        // Insert test data that should be cleared by @IsolatedEventStore before the next test
        val url = TestContainers.postgres.jdbcUrl
        val user = TestContainers.postgres.username
        val password = TestContainers.postgres.password

        DriverManager.getConnection(url, user, password).use { conn ->
            conn.createStatement().execute("INSERT INTO eaf_events.events (payload) VALUES ('test-event')")
        }
    }

    @Test
    @Order(3)
    fun `isolation test - verify IsolatedEventStore truncated previous test data`() {
        // Validates @IsolatedEventStore(TRUNCATE) works: data from test 2 should be cleared
        val url = TestContainers.postgres.jdbcUrl
        val user = TestContainers.postgres.username
        val password = TestContainers.postgres.password

        DriverManager.getConnection(url, user, password).use { conn ->
            val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM eaf_events.events")
            rs.next()
            val count = rs.getInt(1)
            assertEquals(0, count, "Event store should be empty due to isolation")
        }
    }
}
