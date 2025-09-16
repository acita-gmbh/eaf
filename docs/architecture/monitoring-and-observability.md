# Monitoring and Observability

  * **Stack:** Micrometer + Prometheus (Metrics), Structured JSON/Logback (Logging), OpenTelemetry (Tracing).
  * **Critical Mandate:** All three pillars (Logs, Metrics, Traces) MUST be automatically tagged with the `tenant_id` (retrieved from the `Tenancy Component`) and the `trace_id` (from OTel), leveraging the mandatory Micrometer Context Propagation.

-----
