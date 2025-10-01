# Component Specifications

## Overview

This document provides explicit file path specifications and component location standards for the EAF framework. These specifications prevent implementation ambiguity and ensure consistent project structure across all stories and development efforts.

## Configuration Components

### Axon Framework Configuration

#### Framework-Level Auto-Configuration (Cross-Cutting Concerns)
- **Location**: `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/AxonConfiguration.kt`
- **Discovery**: Via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- **Purpose**: Framework-level cross-cutting concerns (interceptors, correlation providers)
- **Applies To**: ALL products automatically (when framework-cqrs is on classpath)
- **Examples**:
  - TenantEventMessageInterceptor registration (Story 4.4)
  - TenantCorrelationDataProvider registration (Story 4.4)
  - Global message handler interceptors
- **Characteristics**:
  - Stateless, no application-specific dependencies
  - Auto-discovered by Spring Boot classpath scanning
  - Can be disabled via `eaf.cqrs.tenant-propagation.enabled=false`
  - Follows Spring Boot Starter pattern

#### Product-Level Configuration (Application-Specific)
- **Location**: `products/{application}/src/main/kotlin/com/axians/eaf/{application}/config/AxonConfiguration.kt`
- **Discovery**: Via component scanning in product application
- **Purpose**: Application-specific Axon setup requiring app-level resources
- **Examples**:
  - Event store DataSource configuration
  - Custom aggregate repositories
  - Product-specific event processors
  - Application-specific serializers
- **Characteristics**:
  - Requires application-specific beans (DataSource, EntityManager)
  - Product-specific business logic
  - Manually authored per product

**Architectural Principle**: Cross-cutting framework concerns are auto-configured; application-specific concerns are manually configured.

### Application Configuration
- **Main Application**: `products/licensing-server/src/main/resources/application.yml`
- **Test Configuration**: `products/licensing-server/src/test/resources/application-test.yml`
- **Dev Configuration**: `products/licensing-server/src/main/resources/application-dev.yml`

## REST API Components

### Controllers
- **Base Package**: `framework/web/src/main/kotlin/com/axians/eaf/framework/web/controllers/`
- **Widget Controller**: `framework/web/src/main/kotlin/com/axians/eaf/framework/web/controllers/WidgetController.kt`
- **Health Controller**: `framework/web/src/main/kotlin/com/axians/eaf/framework/web/controllers/HealthController.kt`

### Global Exception Handling
- **Problem Details Handler**: `framework/web/src/main/kotlin/com/axians/eaf/framework/web/advice/GlobalExceptionHandler.kt`
- **Error Response Factory**: `framework/web/src/main/kotlin/com/axians/eaf/framework/web/errors/ErrorResponseFactory.kt`

### API Configuration
- **OpenAPI Config**: `framework/web/src/main/kotlin/com/axians/eaf/framework/web/config/OpenApiConfiguration.kt`
- **Security Config**: `framework/web/src/main/kotlin/com/axians/eaf/framework/web/config/WebSecurityConfiguration.kt`

## Domain Components

### Aggregates

**Framework Pattern** (for reference only - domain aggregates live in products):
- **Base Package Pattern**: `products/{product}/src/main/kotlin/com/axians/eaf/products/{product}/domain/`

**Product Implementations**:
- **Widget Aggregate**: `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/domain/Widget.kt`
- **Widget Errors**: `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/domain/WidgetError.kt`

**Architectural Note**: Domain aggregates belong in product modules, not framework modules. Framework provides infrastructure (base classes, patterns), products provide domain models.

### CQRS Types (Shared API)
- **Commands**: `shared/shared-api/src/main/kotlin/com/axians/eaf/api/{domain}/commands/`
- **Events**: `shared/shared-api/src/main/kotlin/com/axians/eaf/api/{domain}/events/`
- **Queries**: `shared/shared-api/src/main/kotlin/com/axians/eaf/api/{domain}/queries/`

## Projection Components

### Event Handlers

**Framework Pattern** (for reference only - projection handlers live in products):
- **Base Package Pattern**: `products/{product}/src/main/kotlin/com/axians/eaf/products/{product}/projections/`

**Product Implementations**:
- **Widget Projection Handler**: `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/projections/WidgetProjectionHandler.kt`
- **Widget Query Handler**: `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/query/WidgetQueryHandler.kt`

**Architectural Note**: Projection and query handlers contain domain-specific logic and belong in product modules, not framework modules.

### JPA Entities
- **Base Package**: `framework/persistence/src/main/kotlin/com/axians/eaf/framework/persistence/entities/`
- **Widget Projection Entity**: `framework/persistence/src/main/kotlin/com/axians/eaf/framework/persistence/entities/WidgetProjection.kt`
- **Repository**: `framework/persistence/src/main/kotlin/com/axians/eaf/framework/persistence/repositories/WidgetProjectionRepository.kt`

## Testing Components

### Unit Tests

**Framework Pattern** (for reference - domain tests live in products):
- **Base Package Pattern**: `products/{product}/src/test/kotlin/com/axians/eaf/products/{product}/domain/`

**Product Implementations**:
- **Widget Domain Tests**: `products/widget-demo/src/test/kotlin/com/axians/eaf/products/widgetdemo/domain/WidgetTest.kt`
- **Widget Query Tests**: `products/widget-demo/src/test/kotlin/com/axians/eaf/products/widgetdemo/query/WidgetQueryHandlerTest.kt`

### Integration Tests

**Framework Pattern** (for reference - integration tests live in products):
- **Base Package Pattern**: `products/{product}/src/integration-test/kotlin/com/axians/eaf/products/{product}/api/`

**Product Implementations**:
- **Widget Integration Tests**: `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widgetdemo/api/TenantBoundaryValidationIntegrationTest.kt`
- **Widget Test Application**: `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widgetdemo/test/WidgetDemoTestApplication.kt`

**Framework Infrastructure Tests**:
- **Persistence Tests**: `framework/persistence/src/integration-test/kotlin/com/axians/eaf/framework/persistence/`

### Test Utilities
- **Nullable Implementations**: `shared/testing/src/main/kotlin/com/axians/eaf/testing/nullable/`
- **Test Data Builders**: `shared/testing/src/main/kotlin/com/axians/eaf/testing/builders/`
- **Testcontainer Helpers**: `shared/testing/src/main/kotlin/com/axians/eaf/testing/containers/`

## Observability Components

### Metrics Infrastructure
- **Configuration**: `framework/observability/src/main/kotlin/com/axians/eaf/framework/observability/metrics/MetricsConfiguration.kt`
- **Custom Metrics API**: `framework/observability/src/main/kotlin/com/axians/eaf/framework/observability/metrics/CustomMetrics.kt`
- **Command Metrics Interceptor**: `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/interceptors/CommandMetricsInterceptor.kt`
- **Event Metrics Interceptor**: `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/interceptors/TenantEventMessageInterceptor.kt`
- **Prometheus Endpoint Integration Test**: `framework/observability/src/integration-test/kotlin/com/axians/eaf/framework/observability/metrics/PrometheusEndpointIntegrationTest.kt`

### Monitoring Dashboards

A Grafana dashboard should be configured to monitor key metrics, including:
-   `event.processing.lag`: The lag of event processors.
-   `interceptor.overhead.p95`: The 95th percentile of the event interceptor overhead.
-   `tenant.context.set`, `tenant.context.clear`, `tenant.context.threadlocal_removed`: Metrics for tenant context operations.
-   `tenant_event_interceptor_processed_total`: The rate of processed events per tenant.

### Prometheus Tagging Scheme

All metrics are automatically tagged with the following labels:
-   `service_name`: The name of the application instance.
-   `tenant_id`: The identifier of the tenant.

## Security Components

### Authentication
- **JWT Handlers**: `framework/security/src/main/kotlin/com/axians/eaf/framework/security/jwt/`
- **Tenant Context**: `framework/security/src/main/kotlin/com/axians/eaf/framework/security/tenant/TenantContext.kt`
  - **Implementation**: `ThreadLocal<Deque<WeakReference<String>>>` stack to hold tenant IDs, preventing memory leaks and supporting nested contexts.
  - **Monitoring**: Stack depth is monitored to detect context leaks.
  - **Event Metadata Contract**: All Axon events must include a `tenantId` in their metadata. This is enforced by the `TenantEventMessageInterceptor`.

### Multi-Tenancy
- **Request Filters**: `framework/security/src/main/kotlin/com/axians/eaf/framework/security/filters/TenantFilter.kt`
- **Service Interceptors**: `framework/security/src/main/kotlin/com/axians/eaf/framework/security/aop/TenantValidationAspect.kt`

## Module Structure Standards

### Module Organization Pattern
```
framework/{module}/
├── build.gradle.kts                    # Convention plugins
├── src/main/kotlin/com/axians/eaf/framework/{module}/
│   ├── {Module}Module.kt              # Spring Modulith config
│   ├── domain/                        # Domain logic
│   ├── config/                        # Configuration classes
│   ├── adapters/                      # Infrastructure adapters
│   └── services/                      # Application services
├── src/test/kotlin/                   # Unit tests (Kotest)
├── src/integration-test/kotlin/       # Integration tests (Testcontainers)
└── src/konsist-test/kotlin/          # Architecture compliance tests
```

## Validation Rules

### Path Consistency Rules
1. **Package naming**: Always `com.axians.eaf.framework.{module}`
2. **File naming**: PascalCase for classes, kebab-case for configs
3. **Directory structure**: Match Spring Boot conventions
4. **Module boundaries**: Respect Spring Modulith package rules

### Reference Standards
All story references to file paths MUST:
- ✅ **Reference this document**: `[Source: architecture/component-specifications.md#{section}]`
- ✅ **Use exact paths**: Copy paths precisely from this specification
- ✅ **Verify existence**: Check if target module/directory exists before story creation
- ✅ **Note creation needs**: Explicitly state when new directories must be created

## Implementation Notes

**For Story Authors**: Always reference this document when specifying file locations in Dev Notes sections.

**For Developers**: Follow these exact paths unless architecture review determines changes are needed.

**For QA**: Validate all implemented files match these specifications exactly.

---

## Workflow Components

### Flowable-Axon Integration
- **Flowable to Axon Bridge**: `framework/workflow/src/main/kotlin/com/axians/eaf/framework/workflow/delegates/DispatchAxonCommandTask.kt`
  - This `JavaDelegate` dispatches Axon commands from BPMN processes.
- **Axon to Flowable Bridge**: `framework/workflow/src/main/kotlin/com/axians/eaf/framework/workflow/handlers/AxonEventSignalHandler.kt`
  - This Axon event handler signals waiting BPMN processes.

### Correlation and Alerting
- **Two-Step Correlation Query**: Due to a Flowable limitation, a two-step query is required to correlate events to process instances (query by business key, then by process instance ID). This pattern is implemented in `AxonEventSignalHandler.kt`.
- **Tenant Isolation Alerting**: Production monitoring must alert on `TENANT_ISOLATION_VIOLATION` BpmnErrors, which may indicate a security issue.

**Last Updated**: 2025-09-29
**Purpose**: Prevent inferred file paths and ensure documentation ecosystem integrity
