# Story 6.3: Axon Command Gateway Delegate (BPMN → Axon)

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007

---

## User Story

As a framework developer,
I want BPMN service tasks to dispatch Axon commands,
So that workflows can trigger domain operations (Flowable → Axon direction).

---

## Acceptance Criteria

1. ✅ AxonCommandGatewayDelegate.kt implements JavaDelegate for BPMN service tasks
2. ✅ Delegate extracts command details from process variables
3. ✅ CommandGateway.send() dispatches command to Axon
4. ✅ Command results stored back in process variables
5. ✅ Tenant_id propagated from process variable to command
6. ✅ Integration test: BPMN service task → dispatches CreateWidgetCommand → Widget created
7. ✅ Error handling: command rejection triggers BPMN error boundary event
8. ✅ Delegate usage documented with BPMN example

---

## Prerequisites

**Story 6.2** - Tenant-Aware Process Engine

---

## References

- PRD: FR007
- Architecture: Section 8 (Flowable-Axon Bridge - BPMN→Axon)
- Tech Spec: Section 6.3 (BPMN → Axon Integration)
