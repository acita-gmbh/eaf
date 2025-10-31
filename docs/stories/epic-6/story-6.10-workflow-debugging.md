# Story 6.10: Workflow Debugging and Monitoring Tools

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007, FR013 (Debugging capabilities)

---

## User Story

As a framework developer,
I want workflow debugging utilities,
So that I can inspect and troubleshoot running BPMN processes.

---

## Acceptance Criteria

1. ✅ Workflow inspection API: GET /workflow/instances (list active processes)
2. ✅ Process detail API: GET /workflow/instances/:id (variables, current activity, history)
3. ✅ Manual signal API: POST /workflow/instances/:id/signal (for stuck processes)
4. ✅ Workflow metrics: process_started_total, process_completed_total, process_duration, active_instances
5. ✅ Integration test validates all debugging APIs
6. ✅ Workflow debugging documented in docs/how-to/debug-workflows.md
7. ✅ Flowable UI integration guide (optional, for advanced users)

---

## Prerequisites

**Story 6.9** - Workflow Dead Letter Queue

---

## References

- PRD: FR007, FR013
- Tech Spec: Section 3 (FR007, FR013)
