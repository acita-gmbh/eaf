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
            // Ensure containers are started
            val url = TestContainers.postgres.jdbcUrl
            val user = TestContainers.postgres.username
            val password = TestContainers.postgres.password

            DriverManager.getConnection(url, user, password).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("CREATE SCHEMA IF NOT EXISTS eaf_events")
                }
                conn.createStatement().use { stmt ->
                    stmt.execute(
                        "CREATE TABLE IF NOT EXISTS eaf_events.events (id SERIAL PRIMARY KEY, payload TEXT)",
                    )
                }
                conn.createStatement().use { stmt ->
                    stmt.execute(
                        "CREATE TABLE IF NOT EXISTS eaf_events.snapshots (id SERIAL PRIMARY KEY, state TEXT)",
                    )
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
                stmt.execute("INSERT INTO eaf_events.events (payload) VALUES ('test-event')")
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
