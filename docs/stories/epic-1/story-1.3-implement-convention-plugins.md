# Story 1.3: Implement Convention Plugins in build-logic/

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** ready-for-dev
**Story Points:** TBD
**Related Requirements:** FR001, FR008 (Quality Gates)
**Context File:** docs/stories/epic-1/1-3-implement-convention-plugins.context.xml

---

## User Story

As a framework developer,
I want to create Gradle convention plugins for common configurations,
So that all modules share consistent build settings without duplication.

---

## Acceptance Criteria

1. ✅ build-logic/ composite build created
2. ✅ Convention plugins implemented:
   - eaf.kotlin-common.gradle.kts (Kotlin version, compiler settings)
   - eaf.spring-boot.gradle.kts (Spring Boot plugin, dependencies)
   - eaf.quality-gates.gradle.kts (ktlint, Detekt configuration)
   - eaf.testing.gradle.kts (Kotest, Testcontainers, test source sets)
3. ✅ All framework modules apply relevant convention plugins
4. ✅ ./gradlew build compiles all modules with consistent settings
5. ✅ Convention plugins tested with at least one framework module

---

## Prerequisites

**Story 1.2** - Create Multi-Module Structure

---

## Technical Notes

### Convention Plugins Structure

```
build-logic/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/kotlin/
    ├── eaf.kotlin-common.gradle.kts
    ├── eaf.spring-boot.gradle.kts
    ├── eaf.quality-gates.gradle.kts
    └── eaf.testing.gradle.kts
```

### Plugin Responsibilities

**eaf.kotlin-common.gradle.kts:**
- Kotlin version: 2.2.21
- JVM target: 21
- Compiler options: -Xjsr305=strict
- All-open plugin for Spring

**eaf.spring-boot.gradle.kts:**
- Spring Boot plugin: 3.5.7
- Spring dependency management
- Spring Boot starter dependencies

**eaf.quality-gates.gradle.kts:**
- ktlint: 1.7.1
- Detekt: 1.23.8
- Konsist: 0.17.3

**eaf.testing.gradle.kts:**
- Kotest: 6.0.4
- Testcontainers: 1.21.3
- Test source sets: test, integrationTest, propertyTest, fuzzTest, litmusTest

---

## Implementation Checklist

- [ ] Create build-logic/ composite build
- [ ] Implement eaf.kotlin-common.gradle.kts with Kotlin 2.2.21
- [ ] Implement eaf.spring-boot.gradle.kts with Spring Boot 3.5.7
- [ ] Implement eaf.quality-gates.gradle.kts (ktlint, Detekt, Konsist)
- [ ] Implement eaf.testing.gradle.kts (Kotest, Testcontainers, source sets)
- [ ] Apply convention plugins to framework/core module (test)
- [ ] Run `./gradlew build` - verify consistent configuration
- [ ] Commit: "Add Gradle convention plugins for consistent configuration"

---

## Test Evidence

- [ ] All 4 convention plugins compile
- [ ] framework/core applies plugins successfully
- [ ] `./gradlew :framework:core:dependencies` shows correct versions
- [ ] Consistent Kotlin version across all modules

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Convention plugins tested on framework/core
- [ ] All modules compile with consistent settings
- [ ] No version conflicts
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 1.2 - Create Multi-Module Structure
**Next Story:** Story 1.4 - Create Version Catalog

---

## References

- PRD: FR001, FR008
- Architecture: Section 2 (Version Verification Log)
- Tech Spec: Section 2.1 (Core Technology Stack)

---

## Dev Agent Record

### Context Reference
- Story Context: `docs/stories/epic-1/1-3-implement-convention-plugins.context.xml` (generated 2025-11-01)

### Debug Log
(To be filled during implementation)

### Completion Notes
(To be filled after implementation)
