# Story 6.5: Widget Approval Workflow (BPMN Demo)

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007

---

## User Story

As a framework developer,
I want a Widget approval workflow demonstrating Flowable-Axon integration,
So that I have a working example of orchestrated business processes.

---

## Acceptance Criteria

1. ✅ widget-approval.bpmn20.xml process definition created
2. ✅ Workflow: Start → Axon: CreateWidget → Wait for manual approval → Axon: PublishWidget → End
3. ✅ Service tasks use AxonCommandGatewayDelegate
4. ✅ Signal event receives WidgetApprovedEvent from Axon
5. ✅ Process deployed to Flowable engine
6. ✅ Integration test executes complete workflow end-to-end
7. ✅ Workflow visualized in Flowable UI (if available) or documented with diagram
8. ✅ Approval workflow documented as reference example

---

## Prerequisites

**Story 6.4** - Flowable Event Listener

---

## References

- PRD: FR007
- Tech Spec: Section 3 (FR007), Section 6.3
