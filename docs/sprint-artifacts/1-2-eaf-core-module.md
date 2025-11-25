# Story 1.2: eaf-core-module

Status: ready-for-dev

## Story

As a **developer**,\
I want foundational types for error handling and tracing,\
so that all modules use consistent patterns.

## Acceptance Criteria

1. **Result Type & Error Handling**
   - A sealed `Result<T, E>` type exists with `Success` and `Failure` branches.
   - Mapping helpers (`map`, `flatMap`, `fold`, `getOrElse`) are provided.
   - Null-safety respected; no unchecked exceptions for domain flow.
2. **DomainError Hierarchy**
   - `DomainError` sealed class includes at least: `ValidationFailed`, `ResourceNotFound`, `InvalidStateTransition`, `QuotaExceeded`, `InfrastructureError`.
   - Errors are immutable data classes with contextual fields.
3. **Value Objects**
   - `TenantId`, `UserId`, and `CorrelationId` implemented as `@JvmInline value class` wrappers.
   - Factory helpers: `generate()`, `fromString(...)`.
4. **Kotlin 2.2 / Explicit API**
   - Module compiles with K2, explicit API enabled, and no experimental compiler warnings.
5. **Dependency Boundaries**
   - `eaf-core` has zero external runtime dependencies (Kotlin stdlib only).
   - No imports from `de.acci.dvmm.*`; Konsist rule passes.
6. **Quality Gates**
   - Unit tests for Result and DomainError cover success/failure flows.
   - JaCoCo ≥80% line coverage for `eaf-core`.
   - Pitest mutation score ≥70% for `eaf-core`.

## Tasks / Subtasks

- [ ] Implement sealed `Result<T, E>` with mapping/folding helpers and infix success/failure builders. (AC: 1)
- [ ] Define `DomainError` sealed hierarchy with contextual fields and docs. (AC: 2)
- [ ] Create value objects: `TenantId`, `UserId`, `CorrelationId` with `generate()` and `fromString()` helpers. (AC: 3)
- [ ] Enable explicit API + K2 in `eaf-core` (use existing `eaf.kotlin-conventions`). (AC: 4)
- [ ] Ensure `eaf-core` build file uses only cataloged dependencies; no external runtime deps. (AC: 5)
- [ ] Add unit tests: Result mapping/flatMap/fold paths; DomainError equality/serialization; value object generation/parsing round-trips. (AC: 6)
- [ ] Configure coverage + Pitest thresholds for `eaf-core` via `eaf.pitest-conventions`; ensure JaCoCo gate applies. (AC: 6)
- [ ] Run `./gradlew :eaf:eaf-core:test jacocoTestReport pitest`. (AC: 6)

## Dev Notes

### Learnings from Previous Story (1-1-project-scaffolding)
- Story 1.1 status: **done** (sprint-status.yaml) with review resolution on 2025-11-25; no open action items.
- Build conventions and version catalog are authoritative—reuse `libs.versions.toml` for all versions; avoid hardcoded numbers in module or convention files. sourcedocs/sprint-artifacts/1-1-project-scaffolding.md
- Pitest is provided via `eaf.pitest-conventions` (70% threshold); apply to `eaf-core`. sourcedocs/sprint-artifacts/1-1-project-scaffolding.md
- Konsist rules enforce EAF ← DVMM direction; keep `eaf-core` dependency-free and free of Spring imports. sourcedocs/architecture.md

### Architecture & Constraints
- EAF modules must not import `de.acci.dvmm.*`; `eaf-core` must have zero external runtime deps beyond Kotlin stdlib. sourcedocs/architecture.md
- Use Hexagonal boundaries and explicit API; no wildcard imports; favor sealed classes and value objects for primitives. sourcedocs/architecture.md
- Security/Audit expects correlation IDs available for logging/tracing. sourcedocs/security-architecture.md

### Testing Strategy
- Unit tests first; target ≥80% coverage and ≥70% mutation score using `jacocoTestReport` and Pitest. sourcedocs/test-design-system.md
- Validate coroutine/inline helpers for Result do not allocate excessively; ensure value objects are serializable where needed.

### Project Structure Notes
- Keep `eaf-core` isolated from Spring and product modules; apply `eaf.kotlin-conventions` for compiler flags and explicit API.
- Reference existing build logic from Story 1.1; add new source files under `eaf/eaf-core/src/main/kotlin/...`.

### References
- PRD: `docs/prd.md`
- Epic & story breakdown: `docs/epics.md`
- Architecture: `docs/architecture.md`
- Tech Spec Epic 1: `docs/sprint-artifacts/tech-spec-epic-1.md`
- Security architecture: `docs/security-architecture.md`
- Test design system: `docs/test-design-system.md`
- Previous story: `docs/sprint-artifacts/1-1-project-scaffolding.md`

## Dev Agent Record

### Context Reference
- Story context XML: docs/sprint-artifacts/1-2-eaf-core-module.context.xml

### Agent Model Used
- sm-agent (Scrum Master persona), session 2025-11-25

### Debug Log References
- Pitest fails: plugin runs but reports NO_COVERAGE for Result/DomainError mutations (likely JUnit 6 compatibility gap) — see console log from `./gradlew :eaf:eaf-core:test :eaf:eaf-core:jacocoTestReport :eaf:eaf-core:pitest` at 2025-11-25T00:32Z.

### Completion Notes List
- Implemented Result, DomainError, TenantId/UserId/CorrelationId plus unit tests; jacoco test phase passes. Pitest mutation score blocked (0 coverage reported by plugin); requires follow-up resolution before completing tasks.

### File List
- MOD: `eaf/eaf-core/build.gradle.kts`
- NEW: `eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/result/Result.kt`
- NEW: `eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/error/DomainError.kt`
- NEW: `eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/types/Identifiers.kt`
- NEW: `eaf/eaf-core/src/test/kotlin/de/acci/eaf/core/result/ResultTest.kt`
- NEW: `eaf/eaf-core/src/test/kotlin/de/acci/eaf/core/error/DomainErrorTest.kt`
- NEW: `eaf/eaf-core/src/test/kotlin/de/acci/eaf/core/types/IdentifiersTest.kt`

## Change Log

- 2025-11-25: Draft created from epics/tech-spec with SM agent (#create-story).
- 2025-11-25: Story context generated and status set to ready-for-dev.
