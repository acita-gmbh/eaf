# Observability Strategy

**You can't fix what you can't see.**

"Observability" is more than just logging errors. It's about understanding the internal state of the system by looking at its outputs. In DCM, we rely on the "Three Pillars" of observability.

---

## 1. Structured Logging (The "What")

We don't print text; we print JSON data.

*   **Bad:** `log.info("User logged in")` -> `2025-01-01 User logged in`
*   **Good:** `log.info("User logged in", context)` ->
    ```json
    {
      "timestamp": "2025-01-01T12:00:00Z",
      "level": "INFO",
      "message": "User logged in",
      "userId": "u-123",
      "tenantId": "t-456",
      "ip": "192.168.1.1"
    }
    ```

**Why?** Machines can query JSON. We can ask Loki/Splunk: *"Show me all errors for Tenant T-456 in the last hour."* You can't do that easily with plain text.

## 2. Metrics (The "How Much")

Metrics are numbers aggregated over time. We use **Prometheus**.

*   **Counters:** Things that go up. `http_requests_total`, `vms_provisioned_total`.
*   **Gauges:** Current values. `jvm_memory_used`, `active_db_connections`.
*   **Histograms:** Distributions. `http_request_duration_seconds` (P95, P99).

**Why?** Metrics power our **Dashboards** (Grafana) and **Alerts**.
*   *Alert:* "If `http_5xx_errors > 1%` for 5 minutes, page the on-call engineer."

## 3. Distributed Tracing (The "Where")

A single user request might touch the Load Balancer, API, Database, and Keycloak. Tracing ties them all together.

*   **Trace ID:** A unique ID generated at the entry point (e.g., `abc-123`).
*   **Propagation:** This ID is passed in HTTP headers (`X-B3-TraceId`) to every downstream service.
*   **Span:** A specific operation within a trace (e.g., "DB Query: SELECT *").

**Why?** When a user says "My request was slow," we can look up the Trace ID and see exactly which step took 5 seconds.

---

## The DCM Stack

| Pillar | Tool | Usage |
| :--- | :--- | :--- |
| **Logs** | **Loki** | Aggregates JSON logs from all containers. |
| **Metrics** | **Prometheus** | Scrapes `/actuator/prometheus` endpoint. |
| **Traces** | **OpenTelemetry** | Tracks requests across boundaries. |
| **Visualization**| **Grafana** | The single pane of glass for all three. |

## Developer Responsibilities

1.  **Log Meaningfully:** Don't log "Entry/Exit" of every function. Log business events ("VM Provisioned").
2.  **Use Context:** Always include `tenantId` and `requestId` in logs (MDC handles this automatically for you).
3.  **Define Alerts:** If you build a critical feature (e.g., Billing), define the metric that proves it's working.
