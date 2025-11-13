# Story 8.6: Pitest Mutation Testing Configuration

**Epic:** Epic 8 - Code Quality & Architectural Alignment
**Status:** TODO
**Related Requirements:** FR008 (Quality Gates)

---

## User Story

As a framework developer,
I want Pitest mutation testing integrated,
So that test effectiveness is validated (tests actually catch bugs).

---

## Acceptance Criteria

1. ✅ Pitest 1.19.0 plugin added to Gradle quality gates convention
2. ✅ Mutation testing configured for all framework modules
3. ✅ Target: 60-70% mutation coverage (realistic for deprecated Kotlin plugin)
4. ✅ Property tests excluded from mutation testing (exponential time)
5. ✅ Mutation testing runs in nightly CI/CD (~20-30 minutes)
6. ✅ Mutation report generated and archived
7. ✅ Pitest configuration documented in docs/reference/mutation-testing.md

---

## Prerequisites

**Epic 7 complete**

---

## References

- PRD: FR008
- Architecture: ADR-007 (Mutation Testing Target 60-70%), Section 11
- Tech Spec: Section 2.2 (Pitest 1.19.0), Section 9.1
