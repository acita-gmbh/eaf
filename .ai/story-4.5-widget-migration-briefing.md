# Story 4.5 Briefing: Migrate Widget Domain Code to products/widget-demo

## Context for Architect Agent

You are creating Story 4.5 for Epic 4 (Multi-Tenancy Baseline). This story addresses an architectural misalignment discovered during Story 4.4 implementation.

## Background

### Current State (Architectural Debt)

**Problem**: Widget domain code is incorrectly located in `framework/widget/` module.

**What's in framework/widget**:
- `Widget.kt` - Event-sourced aggregate (domain model)
- `WidgetError.kt` - Domain-specific errors
- `WidgetProjectionHandler.kt` - Read-side event handler (domain logic)
- `WidgetQueryHandler.kt` - Query handler (domain logic)
- `WidgetModule.kt` - Spring Modulith module definition
- Integration tests validating widget-specific behavior

**Why This Is Wrong**:
1. **Framework Philosophy Violation**: Frameworks provide INFRASTRUCTURE (like Spring Data's Repository<T>), not DOMAIN MODELS (like actual entities)
2. **Clean Architecture**: Domain entities (inner layer) should not be in infrastructure modules (outer layer)
3. **Maven Publication**: If framework is published to Maven Central, Widget aggregate would incorrectly ship with framework JARs
4. **Reusability**: licensing-server product does NOT reuse Widget - it's not generic infrastructure
5. **Spring Modulith**: "widget" is a domain bounded context, not a framework bounded context

### Why It Exists in framework/

**Historical Context**: Story 2.1 "Define Widget Domain Aggregate" created framework/widget as:
- Walking Skeleton demo (Epic 2: first end-to-end CQRS flow)
- Reference pattern for future aggregates
- Proof-of-concept for CQRS/ES integration

**Original Intent**: TEMPORARY demo/example, not permanent framework component

### Recent Development (Story 4.4)

**Created**: `products/widget-demo/` - Minimal Spring Boot reference implementation
- Purpose: Framework validation and E2E testing
- Pattern: Like Spring Petclinic (spring-projects reference impl)
- Auto-configuration demo: Shows framework usage with ZERO config code

**Architectural Decision**: Framework provides auto-configured infrastructure (Story 4.4's AxonConfiguration), products provide domain models.

## Story 4.5 Objective

**Migrate ALL widget domain code from framework/widget to products/widget-demo**

### What to Move

**From**: `framework/widget/src/main/kotlin/com/axians/eaf/framework/widget/`
**To**: `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/`

**Files to Migrate**:
1. `domain/Widget.kt` → `domain/Widget.kt`
2. `domain/WidgetError.kt` → `domain/WidgetError.kt`
3. `projections/WidgetProjectionHandler.kt` → `projections/WidgetProjectionHandler.kt`
4. `query/WidgetQueryHandler.kt` → `query/WidgetQueryHandler.kt`
5. `WidgetModule.kt` → `WidgetModule.kt` (Spring Modulith metadata)

**Integration Tests**:
- `framework/widget/src/integration-test/` → `products/widget-demo/src/integration-test/`
- Update test application reference if needed

**Shared API** (commands/events - already in correct location):
- `shared/shared-api/src/main/kotlin/com/axians/eaf/api/widget/` - KEEP HERE (shared contract)

### What to Delete

**After migration, DELETE**:
- Entire `framework/widget/` module
- Remove from `settings.gradle.kts`: `include(":framework:widget")`
- Remove from dependency declarations in other modules

### What to Update

**Dependency Changes**:
- `products/widget-demo/build.gradle.kts`: Remove `implementation(project(":framework:widget"))` (no longer exists)
- `framework/persistence/`: Update imports if WidgetProjection references moved classes
- Any other modules depending on framework/widget

**Package Renames**:
- From: `com.axians.eaf.framework.widget.*`
- To: `com.axians.eaf.products.widgetdemo.*`

## Acceptance Criteria for Story 4.5

1. All Widget domain code moved from `framework/widget/` to `products/widget-demo/`
2. `framework/widget/` module completely removed from project
3. Package names updated: `framework.widget.*` → `products.widgetdemo.*`
4. All imports updated across codebase
5. Integration tests migrated and passing
6. products/widget-demo builds and runs successfully
7. NO references to framework/widget remain in any build files or code
8. Documentation updated (component-specifications.md if needed)

## Testing Requirements

**Before Migration**:
- Capture baseline: All existing widget tests passing
- Document test coverage metrics

**After Migration**:
- All migrated tests passing in products/widget-demo
- widget-demo application starts successfully
- No compilation errors anywhere in project
- Quality gates passing (detekt, ktlint)

**Validation**:
```bash
# Should build successfully
./gradlew :products:widget-demo:build

# Should run application
./gradlew :products:widget-demo:bootRun

# framework/widget should not exist
ls framework/widget  # Should error: No such file or directory
```

## Architecture Validation

### Framework Modules After Migration (CORRECT)

**framework/core**: Base abstractions, error handling
**framework/security**: JWT, TenantContext, filters, interceptors
**framework/cqrs**: Axon configuration, tenant propagation interceptors (Story 4.4)
**framework/persistence**: JPA base classes, repository patterns
**framework/web**: REST controllers, exception handlers
**framework/observability**: Metrics, tracing

**All INFRASTRUCTURE - no domain models ✅**

### Product Modules After Migration (CORRECT)

**products/licensing-server**: Licensing domain (future implementation)
**products/widget-demo**: Widget domain + reference implementation

**All DOMAIN LOGIC + APPLICATIONS ✅**

## References

**Architectural Decision**: Based on Story 4.4 deep analysis with:
- Ollama AI consultation
- Gemini architectural reasoning
- Spring Framework patterns (Spring Data, Axon Framework)
- Clean Architecture principles
- Maven publication litmus test

**Quote from Analysis**:
> "If we publish framework to Maven Central, should Widget aggregate be included?
> Answer: NO - that's a business domain, not framework infrastructure."

**Similar Patterns**:
- Spring Petclinic (spring-projects/spring-petclinic) - Reference impl separate from framework
- Spring Boot Samples (spring-boot/samples) - Demos not in framework modules
- Axon Quick Start (axoniq/axon-quick-start) - Example app separate from Axon Framework

## Story Priority

**Priority**: Medium (architectural cleanup, not blocking)
**Epic**: 4 (Multi-Tenancy Baseline)
**Dependencies**: Story 4.4 complete
**Effort**: 2-4 hours (straightforward refactoring)

## Migration Risks

**Low Risk**:
- Mechanical refactoring (move files, update imports)
- Well-defined scope
- Automated refactoring tools can help

**Validation**:
- Build must succeed
- Tests must pass
- No widget references in framework/

## Expected Outcome

**Clean Architecture**:
```text
framework/
├── core/           ✅ Infrastructure
├── security/       ✅ Infrastructure
├── cqrs/           ✅ Infrastructure (w/ autoconfiguration)
├── persistence/    ✅ Infrastructure
├── web/            ✅ Infrastructure
└── (widget removed)

products/
├── licensing-server/  ✅ Domain: Licensing
└── widget-demo/       ✅ Domain: Widget + Reference impl
```

**Result**: Framework modules are publishable libraries, product modules are business applications.

## Notes for Story Creation

- **Story Title**: "Migrate Widget Domain Code from Framework to Product Module"
- **User Story**: As a framework architect, I want Widget domain code in products/widget-demo, so that framework modules contain only infrastructure and can be published as reusable libraries
- **Story Type**: Refactoring/Technical Debt
- **Estimated Complexity**: Small-Medium
- **Testing**: Regression (all existing tests must pass after migration)

---

## Prompt for Architect Agent

Use this briefing to create Story 4.5 with:
- Clear acceptance criteria (8 listed above)
- Detailed tasks/subtasks for migration
- Testing requirements
- Architectural validation checklist
- References to Story 4.4 architectural decision

**Context Provided**: All necessary information is in this briefing. No need to re-analyze - the architectural decision has been made and validated by multiple AI consultations during Story 4.4.