# Story 10.1: Widget Management Domain Model

**Epic:** Epic 10 - Reference Application for MVP Validation
**Status:** TODO
**Related Requirements:** All FRs (comprehensive validation)

---

## User Story

As a framework developer,
I want comprehensive Widget domain model with business logic,
So that the reference app demonstrates realistic aggregate complexity.

---

## Acceptance Criteria

1. ✅ Widget aggregate enhanced from Epic 2 with: status (Draft, Published, Archived), ownership, metadata
2. ✅ Commands: CreateWidget, UpdateWidget, PublishWidget, ArchiveWidget, TransferOwnership
3. ✅ Events for all state transitions
4. ✅ Business rules: Widget can only be published if valid, archived widgets cannot be updated
5. ✅ Value objects: WidgetStatus enum, WidgetMetadata
6. ✅ Axon Test Fixtures for all commands and business rules
7. ✅ Unit tests with Nullable Pattern
8. ✅ All tests pass in <30 seconds

---

## Prerequisites

**Epic 9 complete**

---

## References

- PRD: All FRs (validation), NFR003 (Majlinda validation)
- Tech Spec: Section 3
