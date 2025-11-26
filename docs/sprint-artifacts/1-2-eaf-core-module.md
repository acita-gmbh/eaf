# Story 1.2: eaf-core-module

Status: done

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

- [x] Implement sealed `Result<T, E>` with mapping/folding helpers and infix success/failure builders. (AC: 1)
- [x] Define `DomainError` sealed hierarchy with contextual fields and docs. (AC: 2)
- [x] Create value objects: `TenantId`, `UserId`, `CorrelationId` with `generate()` and `fromString()` helpers. (AC: 3)
- [x] Enable explicit API + K2 in `eaf-core` (use existing `eaf.kotlin-conventions`). (AC: 4)
- [x] Ensure `eaf-core` build file uses only cataloged dependencies; no external runtime deps. (AC: 5)
- [x] Add unit tests: Result mapping/flatMap/fold paths; DomainError equality/serialization; value object generation/parsing round-trips. (AC: 6)
- [x] Configure coverage + Pitest thresholds for `eaf-core` via `eaf.pitest-conventions`; ensure JaCoCo gate applies. (AC: 6)
- [x] Run `./gradlew :eaf:eaf-core:test jacocoTestReport pitest`. (AC: 6)

## Dev Notes

### Learnings from Previous Story (1-1-project-scaffolding)
- Story 1.1 status: **done** (sprint-status.yaml) with review resolution on 2025-11-25; no open action items.
- Build conventions und Version Catalog sind maßgeblich—`libs.versions.toml` nutzen, keine hardcodierten Versionen (Source: docs/sprint-artifacts/1-1-project-scaffolding.md).
- Pitest steht via `eaf.pitest-conventions` (70%-Schwelle) bereit; für `eaf-core` anwenden (Source: docs/sprint-artifacts/1-1-project-scaffolding.md).
- Konsist-Regeln erzwingen EAF ← DVMM; `eaf-core` bleibt dependency-frei und ohne Spring-Imports (Source: docs/architecture.md).

### Architecture & Constraints
- EAF-Module dürfen nicht aus `de.acci.dvmm.*` importieren; `eaf-core` hat keine externen Runtime-Deps außer Kotlin stdlib (Source: docs/architecture.md).
- Hexagonal boundaries und explicit API; keine Wildcard-Imports; bevorzugt sealed classes und Value Objects (Source: docs/architecture.md).
- Security/Audit benötigt Correlation IDs für Logging/Tracing (Source: docs/security-architecture.md).

### Testing Strategy
- Unit tests first; Ziel ≥80% Coverage und ≥70% Mutation Score mit `jacocoTestReport` und Pitest (Source: docs/test-design-system.md).
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
- Execution: `./gradlew :eaf:eaf-core:test :eaf:eaf-core:jacocoTestReport :eaf:eaf-core:pitest` (2025-11-25T00:36Z) — all green, mutation score 100%.

### Completion Notes List
- Implemented Result, DomainError, TenantId/UserId/CorrelationId plus unit tests; Jacoco + Pitest thresholds met (100% mutation on core scope).

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
- 2025-11-25: Senior Developer Review notes appended; status set to done.

## Senior Developer Review (AI)

Reviewer: Wall-E  
Date: 2025-11-25  
Outcome: Approve

### Summary
All acceptance criteria and completed tasks verified with code/test evidence. No issues found.

### Key Findings
- None (no High/Medium/Low findings).

### Acceptance Criteria Coverage
| AC | Description | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Result type with Success/Failure + mapping helpers; no unchecked exceptions | IMPLEMENTED | eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/result/Result.kt:7-86 |
| 2 | DomainError sealed hierarchy with required variants | IMPLEMENTED | eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/error/DomainError.kt:7-25 |
| 3 | TenantId/UserId/CorrelationId as @JvmInline value classes with generate/fromString | IMPLEMENTED | eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/types/Identifiers.kt:5-47 |
| 4 | Kotlin 2.2 K2 + explicit API enabled | IMPLEMENTED | build-logic/conventions/src/main/kotlin/eaf.kotlin-conventions.gradle.kts |
| 5 | Zero external runtime deps; no de.acci.dvmm imports | IMPLEMENTED | eaf/eaf-core/build.gradle.kts:6-24 |
| 6 | Unit tests + coverage/mutation thresholds met | IMPLEMENTED | eaf/eaf-core/src/test/kotlin/de/acci/eaf/core/result/ResultTest.kt:10-114; eaf/eaf-core/src/test/kotlin/de/acci/eaf/core/error/DomainErrorTest.kt:8-37; eaf/eaf-core/src/test/kotlin/de/acci/eaf/core/types/IdentifiersTest.kt:11-43; gradle run 2025-11-25T00:52Z |

Summary: 6 of 6 ACs fully implemented.

### Task Completion Validation
| Task | Marked As | Verified As | Evidence |
| --- | --- | --- | --- |
| Implement Result with helpers (AC1) | [x] | VERIFIED COMPLETE | eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/result/Result.kt:7-86 |
| Define DomainError hierarchy (AC2) | [x] | VERIFIED COMPLETE | eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/error/DomainError.kt:7-25 |
| Create TenantId/UserId/CorrelationId (AC3) | [x] | VERIFIED COMPLETE | eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/types/Identifiers.kt:5-47 |
| Enable explicit API + K2 (AC4) | [x] | VERIFIED COMPLETE | build-logic/conventions/src/main/kotlin/eaf.kotlin-conventions.gradle.kts |
| Ensure zero external runtime deps (AC5) | [x] | VERIFIED COMPLETE | eaf/eaf-core/build.gradle.kts:6-24 |
| Add unit tests (AC6) | [x] | VERIFIED COMPLETE | ResultTest.kt:10-114; DomainErrorTest.kt:8-37; IdentifiersTest.kt:11-43 |
| Configure coverage + Pitest thresholds (AC6) | [x] | VERIFIED COMPLETE | eaf/eaf-core/build.gradle.kts:6-24; pitest targets core namespace |
| Run gradle tests/jacoco/pitest (AC6) | [x] | VERIFIED COMPLETE | gradle run 2025-11-25T00:52Z |

Summary: 8 of 8 completed tasks verified; 0 questionable; 0 false completions.

### Test Coverage and Gaps
- Unit tests cover success/failure branches of Result, DomainError getters, UUID round-trips.
- Pitest mutation score 100%, Jacoco executed for module.

### Architectural Alignment
- EAF core free of external runtime deps; no DVMM imports. Explicit API and Kotlin 2.2 K2 per conventions.

### Security Notes
- CorrelationId available for tracing; InfrastructureError uses string cause to keep zero deps.

### Best-Practices and References
- Architecture: docs/architecture.md
- Test design: docs/test-design-system.md

### Action Items

**Code Changes Required:**  
- None.

**Advisory Notes:**  
- Note: When adding downstream consumers, preserve zero-runtime-dep rule in eaf-core.
