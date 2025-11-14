# Story 9.11: Working Code Examples

**Epic:** Epic 9 - Golden Path Documentation
**Status:** TODO
**Related Requirements:** FR015

---

## User Story

As an EAF developer,
I want fully working code examples for common patterns,
So that I can copy and adapt them for my use cases.

---

## Acceptance Criteria

1. ✅ docs/examples/ directory with complete, compilable examples:
   - simple-widget/ (minimal CQRS example from Getting Started)
   - multi-tenant-order/ (production example with all features)
   - saga-payment/ (complex workflow with saga and compensation)
2. ✅ Each example includes: README, source code, tests, deployment instructions
3. ✅ Examples buildable and testable (./gradlew :examples:simple-widget:test)
4. ✅ Examples documented with inline comments explaining patterns
5. ✅ Examples reference relevant documentation sections
6. ✅ All examples pass quality gates

---

## Prerequisites

**Story 9.8**

---

## References

- PRD: FR015
- Tech Spec: Section 3 (FR015)
