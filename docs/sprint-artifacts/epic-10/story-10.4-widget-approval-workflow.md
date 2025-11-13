# Story 10.4: Widget Approval BPMN Workflow

**Epic:** Epic 10 - Reference Application for MVP Validation
**Status:** TODO
**Related Requirements:** FR007

---

## User Story

As a framework developer,
I want Widget approval workflow using Flowable BPMN,
So that the reference app demonstrates workflow orchestration.

---

## Acceptance Criteria

1. ✅ widget-approval.bpmn20.xml workflow: Create Draft → Submit for Approval → Manual Review → Approve/Reject → Publish/Archive
2. ✅ BPMN service tasks dispatch: CreateWidgetCommand, PublishWidgetCommand, ArchiveWidgetCommand
3. ✅ Axon events signal workflow: WidgetSubmittedEvent → trigger approval task
4. ✅ Tenant-aware workflow (tenant_id in process variables)
5. ✅ Compensating transaction: Approval rejected → archive widget
6. ✅ Integration test executes full workflow end-to-end
7. ✅ Workflow visualized with diagram in documentation

---

## Prerequisites

**Story 10.3**

---

## References

- PRD: FR007
- Tech Spec: Section 6.3 (Flowable-Axon Bridge)
