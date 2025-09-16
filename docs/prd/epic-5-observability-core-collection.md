# Epic 5: Observability (Core Collection)

**Epic Goal:** This epic delivers the core data *collection* infrastructure for observability, fulfilling our MVP scope. This epic does *not* include building dashboards (like Grafana), which is deferred to Post-MVP. This epic solves the "Limited Observability" pain point of the legacy DCA framework and is a non-negotiable MVP requirement.

### Story 5.1: Implement Standardized Structured Logging
* **As an** Operator, **I want** all framework and application logs to be output in a standardized, structured JSON format, **so that** they can be easily ingested and parsed by an external logging stack (like Loki/Grafana, per the prototype roadmap).
* **AC 1:** A logging convention (e.g., via a Logback/Logstash encoder configuration) is defined in the `build-logic` convention plugins (from Epic 1) and applied to all modules.
* **AC 2:** All log output MUST be in a structured JSON format.
* **AC 3:** All log entries MUST automatically include critical context fields: `service_name`, `trace_id`, and `tenant_id` (retrieved from the `TenantContext` [from Story 4.1]).
* **AC 4:** Integration tests confirm that logs are written in the correct JSON format and include the required context (especially the propagated `tenant_id`).

### Story 5.2: Implement Prometheus Metrics (Micrometer)
* **As an** Operator, **I want** all services built on the EAF to expose standardized application metrics, **so that** we can monitor system health via a Prometheus-compatible endpoint.
* **AC 1:** The Spring Boot Actuator, Micrometer, and Prometheus registry dependencies are added and managed via the convention plugins.
* **AC 2:** The `/actuator/prometheus` endpoint is enabled, secured (requires AuthN from Epic 3), and exposed.
* **AC 3:** Core metrics (JVM, HTTP server requests, Axon message processing) are exposed by default.
* **AC 4:** All relevant metrics are automatically tagged with `tenant_id` (where applicable) and `service_name` to allow for granular filtering.

### Story 5.3: Implement OpenTelemetry (Tracing) Configuration
* **As a** Core Developer, **I want** OpenTelemetry configured by default in the framework, **so that** all requests (API and internal Axon messages) generate and propagate distributed traces.
* **AC 1:** The necessary OpenTelemetry (OTel) dependencies and agent (if applicable) are integrated into the core framework.
* **AC 2:** Trace context (Trace IDs, Span IDs) is automatically propagated across all API requests and asynchronous Axon Commands/Events.
* **AC 3:** The active `trace_id` is automatically injected into the structured JSON logs (from Story 5.1) for log/trace correlation.

---
