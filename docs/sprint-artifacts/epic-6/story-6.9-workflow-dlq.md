# Story 6.9: Workflow Dead Letter Queue and Recovery

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007, FR018 (Error Recovery)

---

## User Story

As a framework developer,
I want dead letter queue for failed workflow messages,
So that Flowable-Axon bridge failures can be investigated and retried.

---

## Acceptance Criteria

1. ✅ Dead letter queue table created for failed Axon commands from BPMN
2. ✅ Failed commands stored with: process instance ID, error details, retry count
3. ✅ Manual retry API: POST /workflow/dlq/:id/retry
4. ✅ Automatic retry with exponential backoff (configurable)
5. ✅ Max retry limit (default: 3) before manual intervention required
6. ✅ Integration test validates: command fails → DLQ → manual retry → success
7. ✅ DLQ monitoring metrics and alerts

---

## Prerequisites

**Story 6.3** - Axon Command Gateway Delegate

---

## References

- PRD: FR007, FR018
- Tech Spec: Section 3 (FR007 - DLQ, FR018 - Retry strategies)
