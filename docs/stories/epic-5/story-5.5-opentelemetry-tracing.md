# Story 5.5: OpenTelemetry Distributed Tracing

**Epic:** Epic 5 - Observability & Monitoring
**Status:** TODO
**Related Requirements:** FR005

---

## User Story

As a framework developer,
I want OpenTelemetry distributed tracing with automatic instrumentation,
So that I can trace requests across REST API and async Axon event processing.

---

## Acceptance Criteria

1. ✅ OpenTelemetry 1.55.0 API/SDK dependencies added
2. ✅ OpenTelemetryConfiguration.kt configures auto-instrumentation
3. ✅ W3C Trace Context propagation enabled (traceparent header)
4. ✅ Automatic spans created for: HTTP requests, Axon commands, Axon events, database queries
5. ✅ trace_id extracted and injected into logs (Story 5.2 integration)
6. ✅ Trace export configured (OTLP exporter, endpoint configurable)
7. ✅ Integration test validates: REST call → command → event → full trace captured
8. ✅ Trace spans include tenant_id as attribute

---

## Prerequisites

**Story 5.2**, **Story 5.4**

---

## References

- PRD: FR005
- Architecture: Section 17 (OpenTelemetry Tracing)
- Tech Spec: Section 2.3 (OpenTelemetry 1.55.0/2.20.1), Section 3 (FR005)
