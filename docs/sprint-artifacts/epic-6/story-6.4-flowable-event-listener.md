# Story 6.4: Flowable Event Listener (Axon → BPMN)

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007

---

## User Story

As a framework developer,
I want Axon event handlers to signal Flowable process instances,
So that workflows can react to domain events (Axon → Flowable direction).

---

## Acceptance Criteria

1. ✅ FlowableEventListener.kt with @EventHandler methods
2. ✅ Event handlers call RuntimeService.signalEventReceived() to signal BPMN processes
3. ✅ Signal correlation using process instance ID or business key
4. ✅ Tenant_id from event metadata used to filter process instances
5. ✅ Integration test: Axon WidgetPublishedEvent → signals waiting BPMN process
6. ✅ Process continues after signal received
7. ✅ Bidirectional integration validated: BPMN ↔ Axon ↔ BPMN
8. ✅ Signal patterns documented

---

## Prerequisites

**Story 6.3** - Axon Command Gateway Delegate

---

## References

- PRD: FR007
- Architecture: Section 8 (Flowable-Axon Bridge - Axon→BPMN)
- Tech Spec: Section 6.3 (Bidirectional Bridge)
