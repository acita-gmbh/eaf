# Story 1.8: Spring Modulith Module Boundary Enforcement

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR010 (Hexagonal Architecture)

---

## User Story

As a framework developer,
I want Spring Modulith configured to enforce module boundaries,
So that architectural violations are caught at compile time.

---

## Acceptance Criteria

1. ✅ Spring Modulith 1.4.4 dependency added to framework modules
2. ✅ Konsist 0.17.3 architecture tests created in shared/testing module
3. ✅ Tests verify: package-by-feature structure, no cyclic dependencies, hexagonal architecture compliance
4. ✅ Module boundary violations fail the build
5. ✅ Spring Modulith documentation generated (module canvas)
6. ✅ ./gradlew check includes Konsist architecture validation
7. ✅ All current modules pass boundary validation

---

## Prerequisites

**Story 1.7** - DDD Base Classes

---

## Technical Notes

### Spring Modulith 1.4.4 Configuration

**framework/core/build.gradle.kts:**
```kotlin
dependencies {
    implementation("org.springframework.modulith:spring-modulith-api")
    testImplementation("org.springframework.modulith:spring-modulith-test")
}
```

### Konsist Architecture Tests

**shared/testing/src/test/kotlin/ArchitectureTest.kt:**
```kotlin
class ArchitectureTest : FreeSpec({
    "framework modules should follow package-by-feature" {
        Konsist
            .scopeFromProject()
            .classes()
            .withNameEndingWith("Controller")
            .shouldResideInPackage("..api..")
    }

    "no cyclic dependencies between modules" {
        Konsist
            .scopeFromProject()
            .assertNoCyclicDependencies()
    }

    "domain layer should not depend on infrastructure" {
        Konsist
            .scopeFromProject()
            .classes()
            .withPackage("..domain..")
            .shouldNotDependOn("..infrastructure..")
    }

    "hexagonal architecture compliance" {
        // Domain → Ports → Adapters
        Konsist
            .scopeFromProject()
            .classes()
            .withPackage("..domain..")
            .shouldNotDependOn("..adapter..")
    }
})
```

### Module Boundary Rules

- **framework/core** - No dependencies (base layer)
- **framework/security** - May depend on core
- **framework/multi-tenancy** - May depend on core, security
- **framework/cqrs** - May depend on core
- **framework/persistence** - May depend on core, cqrs
- **framework/observability** - May depend on core
- **framework/workflow** - May depend on core, cqrs
- **framework/web** - May depend on core, security

---

## Implementation Checklist

- [ ] Add Spring Modulith 1.4.4 to version catalog
- [ ] Add Spring Modulith dependencies to framework modules
- [ ] Create shared/testing module
- [ ] Add Konsist 0.17.3 to shared/testing dependencies
- [ ] Implement ArchitectureTest.kt with Konsist rules
- [ ] Add architecture tests to CI/CD (./gradlew check)
- [ ] Run `./gradlew :shared:testing:test` - verify tests pass
- [ ] Generate Spring Modulith module canvas
- [ ] Document module boundaries in architecture.md
- [ ] Commit: "Add Spring Modulith and Konsist for boundary enforcement"

---

## Test Evidence

- [ ] Konsist architecture tests compile
- [ ] `./gradlew :shared:testing:test` passes
- [ ] `./gradlew check` includes architecture validation
- [ ] Intentional boundary violation fails build
- [ ] Spring Modulith module canvas generated

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Architecture tests pass
- [ ] Module boundaries documented
- [ ] Violations fail the build
- [ ] Module canvas generated
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 1.7 - DDD Base Classes
**Next Story:** Story 1.9 - CI/CD Pipeline Foundation

---

## References

- PRD: FR010 (Hexagonal Architecture)
- Architecture: Section 4 (Spring Modulith 1.4.4 decision)
- Tech Spec: Section 3 (FR010 Implementation)
