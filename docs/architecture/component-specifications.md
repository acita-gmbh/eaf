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
- **Domain Tests**: `framework/{domain}/src/test/kotlin/com/axians/eaf/framework/{domain}/domain/`
- **Widget Domain Tests**: `framework/widget/src/test/kotlin/com/axians/eaf/framework/widget/domain/WidgetTest.kt`

### Integration Tests
- **API Integration**: `framework/{domain}/src/integration-test/kotlin/com/axians/eaf/framework/{domain}/api/`
- **Widget API Tests**: `framework/widget/src/integration-test/kotlin/com/axians/eaf/framework/widget/api/WidgetApiIntegrationTest.kt`
- **Persistence Tests**: `framework/persistence/src/integration-test/kotlin/com/axians/eaf/framework/persistence/`

### Test Utilities
- **Nullable Implementations**: `shared/testing/src/main/kotlin/com/axians/eaf/testing/nullable/`
- **Test Data Builders**: `shared/testing/src/main/kotlin/com/axians/eaf/testing/builders/`
- **Testcontainer Helpers**: `shared/testing/src/main/kotlin/com/axians/eaf/testing/containers/`

## Security Components

### Authentication
- **JWT Handlers**: `framework/security/src/main/kotlin/com/axians/eaf/framework/security/jwt/`
- **Tenant Context**: `framework/security/src/main/kotlin/com/axians/eaf/framework/security/tenant/TenantContext.kt`

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

**Last Updated**: 2025-09-20
**Purpose**: Prevent inferred file paths and ensure documentation ecosystem integrity