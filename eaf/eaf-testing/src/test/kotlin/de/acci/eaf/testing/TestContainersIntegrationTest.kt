package de.acci.eaf.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.sql.DriverManager

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
    fun `isolation test - insert data`() {
        // Insert data
        val url = TestContainers.postgres.jdbcUrl
        val user = TestContainers.postgres.username
        val password = TestContainers.postgres.password

        DriverManager.getConnection(url, user, password).use { conn ->
            conn.createStatement().execute("INSERT INTO eaf_events.events (payload) VALUES ('test-event')")
        }
    }

    @Test
    @Order(3)
    fun `isolation test - verify data is gone`() {
        // Should be empty because @IsolatedEventStore TRUNCATEs before each test
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
