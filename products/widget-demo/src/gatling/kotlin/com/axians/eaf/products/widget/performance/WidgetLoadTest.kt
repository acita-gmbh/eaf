package com.axians.eaf.products.widget.performance

import io.gatling.javaapi.core.CoreDsl
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl
import java.time.Duration

/**
 * Gatling load test for Widget CRUD API endpoints.
 *
 * Load Test Scenarios:
 * - 10 concurrent users immediately
 * - Ramp to 100 concurrent users over 30 seconds
 * - Sustained load approximately 1000 requests per second
 *
 * Performance Targets (FR011, NFR001):
 * - API p95 latency less than 200ms
 * - Success rate greater than 99 percent
 *
 * Test Pattern:
 * 1. Create widget via POST
 * 2. Verify 201 Created response
 * 3. Wait 1 second
 * 4. List widgets via GET
 * 5. Verify 200 OK response
 *
 * Usage: Run with gradlew gatlingRun
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
            ).pause(Duration.ofSeconds(1))
            .exec(
                HttpDsl
                    .http("List Widgets")
                    .get("/api/v1/widgets?limit=50")
                    .check(HttpDsl.status().`is`(200)),
            )

    init {
        setUp(
            widgetCrudScenario.injectOpen(
                CoreDsl.atOnceUsers(10),
                CoreDsl.rampUsers(100).during(Duration.ofSeconds(30)),
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
