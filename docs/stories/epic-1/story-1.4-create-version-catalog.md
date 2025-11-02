# Story 1.4: Create Version Catalog with Verified Dependencies

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** ready-for-dev
**Story Points:** TBD
**Related Requirements:** FR001
**Context File:** docs/stories/epic-1/1-4-create-version-catalog.context.xml

---

## User Story

As a framework developer,
I want to define all dependency versions in gradle/libs.versions.toml,
So that version management is centralized and consistent across all modules.

---

## Acceptance Criteria

1. ✅ gradle/libs.versions.toml created with all 28+ dependencies
2. ✅ Core stack versions verified: Kotlin 2.2.21, Spring Boot 3.5.7, Spring Modulith 1.4.4, Axon 4.12.1, PostgreSQL 16.10
3. ✅ Testing stack versions: Kotest 6.0.4, Testcontainers 1.21.3, Pitest 1.19.0, ktlint 1.7.1, Detekt 1.23.8
4. ✅ All framework modules use version catalog references (no hardcoded versions)
5. ✅ ./gradlew dependencies shows correct version resolution
6. ✅ Version catalog validated against architecture.md specifications

---

## Prerequisites

**Story 1.3** - Implement Convention Plugins

---

## Technical Notes

### Verified Versions (from architecture.md Section 2)

All versions verified 2025-10-30/31:

**Core Stack:**
- Kotlin: 2.2.21
- Spring Boot: 3.5.7
- Spring Modulith: 1.4.4
- Axon Framework: 4.12.1
- PostgreSQL: 16.10
- jOOQ: 3.20.8
- Keycloak: 26.4.2
- Flowable BPMN: 7.2.0
- Gradle: 9.1.0

**Testing Stack:**
- Kotest: 6.0.4
- Testcontainers: 1.21.3
- Jazzer: 0.25.1
- Pitest: 1.19.0
- ktlint: 1.7.1
- Detekt: 1.23.8
- Konsist: 0.17.3
- Kover: 0.9.3

**Infrastructure:**
- Docker Compose: 2.40.3
- Redis: 7.2
- Prometheus (Micrometer): 1.15.5
- OpenTelemetry: 1.55.0 (API/SDK) / 2.20.1 (instrumentation)
- Logback: 1.5.19
- Grafana: 12.2

**Developer Experience:**
- Picocli: 4.7.7
- Mustache: 0.9.14
- Springdoc OpenAPI: 2.6.0
- Dokka: 2.1.0

---

## Implementation Checklist

- [ ] Create gradle/libs.versions.toml
- [ ] Define [versions] section with all 28+ dependencies
- [ ] Define [libraries] section with version references
- [ ] Define [plugins] section (Kotlin, Spring Boot, etc.)
- [ ] Update convention plugins to use version catalog
- [ ] Update framework modules to use version catalog
- [ ] Run `./gradlew dependencies` - verify resolution
- [ ] Cross-check versions against architecture.md Section 2
- [ ] Commit: "Add centralized version catalog with verified dependencies"

---

## Test Evidence

- [ ] gradle/libs.versions.toml contains all 28+ dependencies
- [ ] No hardcoded versions in build.gradle.kts files
- [ ] `./gradlew dependencies` shows correct versions
- [ ] Versions match architecture.md Section 2 verification log

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] All versions verified against architecture.md
- [ ] No hardcoded dependency versions
- [ ] Dependency resolution succeeds
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 1.3 - Implement Convention Plugins
**Next Story:** Story 1.5 - Docker Compose Development Stack

---

## References

- PRD: FR001
- Architecture: Section 2 (Version Verification Log)
- Tech Spec: Section 2 (Technology Stack)

---

## Dev Agent Record

### Context Reference
- Story Context: `docs/stories/epic-1/1-4-create-version-catalog.context.xml` (generated 2025-11-02)
