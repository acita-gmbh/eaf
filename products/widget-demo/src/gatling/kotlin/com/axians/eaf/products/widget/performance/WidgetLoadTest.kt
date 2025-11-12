package com.axians.eaf.products.widget.performance

import io.gatling.javaapi.core.CoreDsl
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl
import java.time.Duration

/**
 * Gatling load test for Widget CRUD API endpoints with Keycloak JWT authentication.
 *
 * Load Test Scenarios:
 * - Warm-up: 10 users immediately
 * - Ramp: 0 → 500 users/second over 1 minute
 * - Sustained: 500 users/second for 2 minutes
 * - Total: ~1000 requests/second sustained (2 requests per user)
 *
 * Performance Targets (FR011, NFR001):
 * - API p95 latency less than 200ms
 * - Success rate greater than 99%
 *
 * Test Pattern (per user):
 * 1. Authenticate with Keycloak (get JWT token)
 * 2. Create widget via POST with Authorization header
 * 3. Verify 201 Created response
 * 4. Pause 100ms (realistic think time)
 * 5. List widgets via GET with Authorization header
 * 6. Verify 200 OK response
 *
 * Usage: Run with gradlew gatlingRun (requires widget-demo on port 8090)
 * Results: build/reports/gatling/widgetloadtest-timestamp/index.html
 *
 * Story 2.13: Performance Baseline and Monitoring
 */
class WidgetLoadTest : Simulation() {
    private val httpProtocol =
        HttpDsl.http
            .baseUrl("http://localhost:8090")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .userAgentHeader("Gatling-EAF-v1.0")

    private val widgetCrudScenario =
        CoreDsl
            .scenario("Widget CRUD Load Test")
            // Step 1: Get JWT token from Keycloak
            .exec(
                HttpDsl
                    .http("Keycloak Token")
                    .post("http://localhost:8080/realms/eaf/protocol/openid-connect/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .formParam("grant_type", "password")
                    .formParam("client_id", "eaf-api")
                    .formParam("client_secret", "eaf-api-secret-development-only")
                    .formParam("username", "admin")
                    .formParam("password", "admin")
                    .check(HttpDsl.status().`is`(200))
                    .check(HttpDsl.jmesPath("access_token").saveAs("accessToken")),
            )
            // Step 2: Create widget with Authorization header
            .exec(
                HttpDsl
                    .http("Create Widget")
                    .post("/api/v1/widgets")
                    .header("Authorization", "Bearer #{accessToken}")
                    .body(CoreDsl.StringBody("""{"name":"LoadTestWidget"}"""))
                    .check(HttpDsl.status().`is`(201)),
            )
            // Step 3: Realistic think time
            .pause(Duration.ofMillis(100))
            // Step 4: List widgets with Authorization header
            .exec(
                HttpDsl
                    .http("List Widgets")
                    .get("/api/v1/widgets?limit=50")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(HttpDsl.status().`is`(200)),
            )

    init {
        setUp(
            widgetCrudScenario.injectOpen(
                // Warm-up phase: 10 users immediately
                CoreDsl.atOnceUsers(10),
                // Ramp phase: 0 → 500 users/sec over 1 minute
                CoreDsl.rampUsersPerSec(0.0).to(500.0).during(Duration.ofSeconds(60)),
                // Sustained load: 500 users/sec for 2 minutes
                // → 500 users/sec × 2 requests/user = 1000 req/sec sustained
                CoreDsl.constantUsersPerSec(500.0).during(Duration.ofMinutes(2)),
            ),
        ).protocols(httpProtocol)
            .assertions(
                CoreDsl
                    .global()
                    .responseTime()
                    .percentile3()
                    .lt(200),
                CoreDsl
                    .global()
                    .successfulRequests()
                    .percent()
                    .gt(99.0),
            )
    }
}
