package com.axians.eaf.products.widget.performance

import io.gatling.javaapi.core.CoreDsl
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl
import java.time.Duration

/**
 * Gatling load test for Widget CRUD API endpoints.
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
 * 1. Create widget via POST
 * 2. Verify 201 Created response
 * 3. Pause 100ms (realistic think time)
 * 4. List widgets via GET
 * 5. Verify 200 OK response
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
            .exec(
                HttpDsl
                    .http("Create Widget")
                    .post("/api/v1/widgets")
                    .body(CoreDsl.StringBody("""{"name":"LoadTestWidget"}"""))
                    .check(HttpDsl.status().`is`(201)),
            ).pause(Duration.ofMillis(100)) // Realistic think time
            .exec(
                HttpDsl
                    .http("List Widgets")
                    .get("/api/v1/widgets?limit=50")
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
