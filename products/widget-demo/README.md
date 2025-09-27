# EAF Widget Demo Application

**Purpose**: Reference implementation and E2E testing application for the Enterprise Application Framework (EAF).

## What This Is

This is a **minimal Spring Boot application** that demonstrates proper consumption of the EAF framework modules. It serves as:

1. **Reference Implementation**: Shows how products should integrate with framework
2. **E2E Test Host**: Provides full application context for integration tests  
3. **Framework Validation**: Proves auto-configuration works correctly
4. **Developer Guide**: Example for teams building products on EAF

## Auto-Configured Features

By depending on framework modules, this application automatically receives:

### Multi-Tenancy (Stories 4.1-4.4)
- ✅ Layer 1: JWT extraction → TenantContext
- ✅ Layer 2: Command handler validation
- ✅ Layer 3: PostgreSQL RLS enforcement
- ✅ **Layer 4: Async event processor propagation** (Story 4.4)

### CQRS/Event Sourcing
- ✅ Axon Framework with tenant-aware interceptors
- ✅ Event metadata enrichment with tenantId
- ✅ Fail-closed security design

### Security
- ✅ 10-layer JWT validation
- ✅ Keycloak OIDC integration
- ✅ CWE-209 compliant error messages

## Zero Configuration Example

Notice: **No AxonConfiguration class in this product!**

The `TenantEventMessageInterceptor` and `TenantCorrelationDataProvider` are automatically registered via:

```
framework-cqrs/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

This demonstrates the Spring Boot Auto-Configuration pattern (Story 4.4 architectural decision).

## Running

```bash
# Start infrastructure
./scripts/init-dev.sh

# Run application
./gradlew :products:widget-demo:bootRun

# Run E2E tests
./gradlew :products:widget-demo:integrationTest
```

## E2E Tests

This product hosts E2E tests that validate framework integration:
- `TenantContextPropagationE2EIntegrationTest` (Story 4.4)
- Full CQRS cycle with PostgreSQL + RLS
- Multi-tenant isolation validation
- Performance benchmarking

## Comparison to Other Patterns

Similar to:
- **Spring Petclinic**: Reference impl for Spring Boot
- **Axon Quick Start**: Minimal Axon application
- **Spring Boot Samples**: Testing and demonstration apps

This is NOT a production product - it's a **framework validation and reference tool**.
