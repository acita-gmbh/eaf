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

## Technical Notes

### OpenTelemetry Version Alignment (from Story 2.7)

**Current State (Story 2.7):**

OpenTelemetry dependencies are **temporarily excluded** from `products/widget-demo` to avoid version conflict:

```kotlin
// products/widget-demo/build.gradle.kts
configurations.all {
    exclude(group = "io.opentelemetry")
    exclude(group = "io.opentelemetry.instrumentation")
    exclude(group = "io.opentelemetry.semconv")
}
```

**Reason for Exclusion:**
- **Spring Boot 3.5.7** manages OpenTelemetry **1.49.0** (via dependency management BOM)
- **Framework modules** (via ObservabilityConventionPlugin) bring OpenTelemetry **1.55.0**
- **API Incompatibility:** `AutoConfiguredOpenTelemetrySdkBuilder.setComponentLoader()` method signature changed between versions
- **Result:** NoSuchMethodError in Spring Boot OpenTelemetryAutoConfiguration

**Version Catalog:**
```toml
opentelemetry-bom = "1.55.0"  # Framework version
opentelemetry-instrumentation = "2.21.0"
```

**Resolution Strategy for Story 5.5:**

**Option A (Recommended):** Align framework to Spring Boot BOM version
- Change `opentelemetry-bom = "1.55.0"` → `"1.49.0"`
- Verify all framework/observability, framework/cqrs, framework/security modules compatible
- Remove exclusions from widget-demo

**Option B:** Wait for Spring Boot 3.6+ which may support OpenTelemetry 1.55.0
- Continue with exclusion strategy
- Re-evaluate when Spring Boot BOM updates

**Implementation Checklist Addition:**
- [ ] Resolve OpenTelemetry version conflict (choose Option A or B)
- [ ] Remove temporary exclusions from products/widget-demo/build.gradle.kts
- [ ] Verify Spring Boot OpenTelemetryAutoConfiguration initializes successfully
- [ ] Test auto-instrumentation with both versions aligned

**Context:** Story 2.7 successfully uses Micrometer (via Actuator) for metrics without OpenTelemetry. This story activates full distributed tracing which requires OpenTelemetry dependencies.

**References:**
- Spring Boot 3.5.7 Dependency Coordinates: https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html
- Story 2.7 analysis: OpenTelemetry version mismatch investigation
- ObservabilityConventionPlugin: Lines 22-32 add OpenTelemetry 1.55.0 to all modules

---

## References

- PRD: FR005
- Architecture: Section 17 (OpenTelemetry Tracing)
- Tech Spec: Section 2.3 (OpenTelemetry 1.55.0/2.20.1), Section 3 (FR005)
