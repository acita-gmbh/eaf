# Story 6.1: Flowable BPMN Engine Configuration

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007 (Workflow Orchestration with Recovery)

---

## User Story

As a framework developer,
I want Flowable BPMN engine integrated with dedicated PostgreSQL schema,
So that I can define and execute business workflows.

---

## Acceptance Criteria

1. ✅ framework/workflow module created with Flowable 7.2.0 dependencies
2. ✅ FlowableConfiguration.kt configures ProcessEngine, RuntimeService, TaskService
3. ✅ Dedicated PostgreSQL schema for Flowable tables (ACT_* tables)
4. ✅ Flyway migration creates Flowable schema
5. ✅ Flowable engine starts successfully on application startup
6. ✅ Integration test deploys and starts simple BPMN process
7. ✅ Flowable process tables visible in PostgreSQL

---

## Prerequisites

**Epic 5 complete**

---

## References

- PRD: FR007
- Architecture: Section 8 (Workflow Stack - Flowable 7.2.0)
- Tech Spec: Section 2.1 (Flowable BPMN 7.2.0), Section 3 (FR007)
