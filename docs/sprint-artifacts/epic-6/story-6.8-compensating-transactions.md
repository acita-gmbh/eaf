# Story 6.8: Compensating Transactions for Workflow Errors

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007

---

## User Story

As a framework developer,
I want compensating transaction support in BPMN workflows,
So that partial failures can be rolled back gracefully.

---

## Acceptance Criteria

1. ✅ BPMN error boundary events configured with compensation handlers
2. ✅ Compensation patterns documented: undo commands, reverse operations, cleanup tasks
3. ✅ Example: Widget creation fails → compensation deletes partial data
4. ✅ Integration test validates: trigger error → compensation executes → state restored
5. ✅ Saga pattern integration for multi-step compensations
6. ✅ Compensating transactions logged and traced
7. ✅ Compensation patterns documented in docs/reference/workflow-patterns.md

---

## Prerequisites

**Story 6.5** - Widget Approval Workflow

---

## References

- PRD: FR007 (Compensating transactions)
- Tech Spec: Section 3 (FR007)
