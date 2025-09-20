# Component Specifications

## Overview

This document provides explicit file path specifications and component location standards for the EAF framework. These specifications prevent implementation ambiguity and ensure consistent project structure across all stories and development efforts.

## Configuration Components

### Axon Framework Configuration
- **Primary Config**: `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/AxonConfiguration.kt`
- **Event Store Config**: `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/EventStoreConfiguration.kt`
- **Command Gateway Config**: `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/CommandBusConfiguration.kt`
- **Query Gateway Config**: `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/QueryBusConfiguration.kt`

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
- **Base Package**: `framework/{domain}/src/main/kotlin/com/axians/eaf/framework/{domain}/domain/`
- **Widget Aggregate**: `framework/widget/src/main/kotlin/com/axians/eaf/framework/widget/domain/Widget.kt`
- **Error Types**: `framework/widget/src/main/kotlin/com/axians/eaf/framework/widget/domain/WidgetError.kt`

### CQRS Types (Shared API)
- **Commands**: `shared/shared-api/src/main/kotlin/com/axians/eaf/api/{domain}/commands/`
- **Events**: `shared/shared-api/src/main/kotlin/com/axians/eaf/api/{domain}/events/`
- **Queries**: `shared/shared-api/src/main/kotlin/com/axians/eaf/api/{domain}/queries/`

## Projection Components

### Event Handlers
- **Base Package**: `framework/{domain}/src/main/kotlin/com/axians/eaf/framework/{domain}/projections/`
- **Widget Projection Handler**: `framework/widget/src/main/kotlin/com/axians/eaf/framework/widget/projections/WidgetProjectionHandler.kt`

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