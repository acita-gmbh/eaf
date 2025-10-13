# Epic 8: Code Quality & Architectural Alignment

**Epic Goal:** Systematically address critical architectural deviations and technical debt identified through comprehensive codebase analysis, ensuring the EAF framework aligns with architectural specifications before MVP validation (Epic 9).

---

## Epic Description

### Context

The EAF framework (v0.1) has been developed through Epics 1-7 with comprehensive architectural documentation. A deep codebase investigation combined with architectural review (Winston) and analytical validation (Mary) identified **4 critical gaps** requiring resolution before Epic 9 (Licensing Server MVP). During Story 8.1 implementation, a 5th story was added to establish pre-commit hook infrastructure for automated quality enforcement.

### Validation Methodology

**Investigation Process:**
1. **Initial Analysis**: Deep codebase scan touching thousands of lines
2. **Architectural Review**: Winston (Architect) validated findings against Epic 7 decisions
3. **Analytical Validation**: Mary (Analyst) cross-referenced all findings with architecture docs

**Results:**
- 6 initial findings identified
- 4 findings confirmed as architectural gaps (67% accuracy)
- 2 findings invalidated (architectural misunderstandings corrected)

### Confirmed Architectural Gaps

#### **Gap 1: Read Projections Use JPA Instead of jOOQ** 🚨
- **Architecture Requirement**: "jOOQ for read projections" (docs/architecture/tech-stack.md)
- **Current Implementation**: JPA/Hibernate with @Entity and JpaRepository
- **Root Cause**: Early prototyping in Epic 2 used JPA for speed; jOOQ migration deferred
- **Impact**: Sub-optimal read performance, architectural non-compliance
- **Evidence**: WidgetProjectionRepository extends JpaRepository (should use DSLContext)

#### **Gap 2: 7 Integration Tests Disabled** ⚠️
- **Current State**: 7 critical tests in `kotlin-disabled/` folders not executing
- **Disabled Tests**:
  - Widget domain: WidgetIntegrationTest, WidgetApiIntegrationTest, WidgetEventStoreIntegrationTest, WidgetWalkingSkeletonIntegrationTest, WidgetEventProcessingIntegrationTest
  - Observability: LoggingContextIntegrationTest, StructuredLoggingIntegrationTest
- **Root Cause**: Story 4.6 discovered Kotest+Spring Boot complexity; disabled to unblock Epic 4
- **Impact**: Test coverage gap, no end-to-end validation
- **Evidence**: Intentional technical debt documented in git history

#### **Gap 3: React-Admin Consumer Application Missing** 🔴
- **Current State**: Micro-frontend architecture partially implemented
  - ✅ Framework admin shell exists (`framework/admin-shell/`) - published npm library
  - ✅ UI resource generator exists (`eaf scaffold ui-resource`) - CLI tool functional
  - ❌ Consumer application missing (`apps/admin/`) - only placeholder build files
- **Root Cause**: Epic 7.4 delivered infrastructure (shell + generator) but not integration layer
- **Impact**: Cannot use React-Admin portal; frontend development blocked
- **Evidence**: apps/admin/ contains only build.gradle.kts (737 bytes) and empty package-lock.json

#### **Gap 4: Inconsistent Test Case Naming Convention** ⚠️
- **Current State**: Test case names lack consistent story reference pattern
- **Evidence**:
  - WidgetQueryHandlerTest: Uses `{STORY}-{TYPE}-{SEQ}: {Description}` (e.g., "2.4-UNIT-001: FindWidgetByIdQuery returns widget response when found") ✅
  - WidgetTest (BehaviorSpec): No story references in Given/When/Then blocks ❌
  - TenantEventMessageInterceptorSpec: MIXED - some tests have IDs ("4.4-UNIT-001"), others don't ❌
- **Root Cause**: No enforced standard for test case naming; evolved organically during development
- **Impact**:
  - Difficult to trace test coverage back to story acceptance criteria
  - Inconsistent test reporting and documentation
  - Quality gate gaps when validating story completion
- **Requirement**: All tests must reference their source story for traceability

### Architectural Misunderstandings Corrected

The following were initially flagged but validated as **architecturally correct**:

1. **Test File Naming** ✅ VALID
   - Mixed `*Test.kt` and `*Spec.kt` is **intentional** (architecture allows both FunSpec and BehaviorSpec)
   - File naming follows test-strategy-and-standards-revision-3.md
   - Note: Test **case** naming within files is Gap 4 (requires standardization)

2. **REST API Layer** ✅ EXISTS
   - WidgetController.kt provides complete CRUD REST API
   - Search error initially missed the controller
   - Architecture compliance verified

3. **Event Sourcing Handlers** ✅ CORRECT
   - @EventSourcingHandler (aggregate reconstitution) vs @EventHandler (projections) correctly separated
   - Standard CQRS/ES pattern properly implemented
   - No architectural gap exists

---

## Stories

### Story 8.1: Standardize Test Case Naming with Story References ✅ DONE

**Status:** Complete (100% compliance achieved)

**As a** QA Engineer, **I want** all test cases to follow a consistent naming convention with story references, **so that** test coverage can be traced back to story acceptance criteria and quality gates can be properly validated.

**Implementation Summary:**
- ✅ 121 FunSpec tests updated with story references across 24 files
- ✅ 6 BehaviorSpec comments added with story references
- ✅ Konsist enforcement rules created and passing (19/19 tests)
- ✅ Compliance improved from 27% to 100%
- ✅ CLI templates updated for future test generation
- Note: Pre-commit hook (AC 16) deferred to Story 8.2

**Estimated Effort:** 3-5 days | **Actual:** 3 days

---

### Story 8.2: Establish Pre-Commit Hook Infrastructure ✅ DONE

**As a** Developer, **I want** automated pre-commit validation infrastructure that enforces code quality standards before commits, **so that** issues are caught locally with fast feedback, reducing CI failures and maintaining consistent code quality.

**Estimated Effort:** 9-10 days | **Actual:** 1 day (MVP)

---

### Story 8.3: Migrate Read Projections from JPA to jOOQ ✅ DONE

**As a** Developer, **I want** to replace JPA-based CQRS read projections with jOOQ DSL, **so that** the read model achieves optimal performance and aligns with architectural specifications.

**Estimated Effort:** 5-8 days | **Actual:** 1 day

---

### Story 8.4: Re-enable and Fix 7 Disabled Integration Tests ✅ DONE

**As a** QA Engineer, **I want** all disabled integration tests re-enabled and passing, **so that** critical system behaviors are continuously validated and test coverage gaps are eliminated.

**Estimated Effort:** 5-7 days | **Actual:** 1 day

---

### Story 8.5: Architectural Patterns Alignment ✅ DONE

**Status:** Complete (100% compliance achieved)

**As a** Developer, **I want** the codebase to consistently apply the Either and Nullable patterns, **so that** the framework adheres to functional programming principles and enables faster, more robust testing.

**Estimated Effort:** 3-5 days | **Actual:** 3 days

---

### Story 8.6: Enable Mutation Testing (Pitest) for Framework Modules ✅ DONE

**As a** Developer, **I want** mutation testing enabled on framework modules, **so that** we validate test quality beyond line coverage.

**Estimated Effort:** 2-3 days | **Actual:** 1 day

---

### Story 8.7: Implement Nightly Deep Validation Pipeline ✅ DONE

**As a** Developer, **I want** a nightly deep validation pipeline separated from PR feedback pipeline, **so that** I get fast PR feedback (<3 minutes) while maintaining comprehensive quality validation through nightly scheduled deep testing.

**Estimated Effort:** 3-4 days | **Actual:** 1 day

---

### Story 8.8: Fix Fuzz Corpus Persistence for Incremental Fuzzing ✅ DONE

**As a** Security Engineer, **I want** fuzz corpus persistence to function correctly in nightly pipeline, **so that** incremental fuzzing can build on previous runs and discover deep security vulnerabilities over time.

**Estimated Effort:** 0.5-1 day | **Actual:** 1 day

---

## Definition of Done

- [x] All 8 stories completed with acceptance criteria met.
- [x] jOOQ projections outperform JPA baseline by ≥20%
- [x] All 7 integration tests passing in CI (<5 min execution)
- [x] Zero architectural violations (Konsist, Detekt, ktlint)
- [x] Test coverage ≥85% line, ≥80% mutation (maintained)
- [x] Documentation updated.
- [x] No regressions in existing functionality.
- [x] Code review approved by Architecture team.
- [x] Epic 9 (Licensing Server MVP) prerequisites satisfied.