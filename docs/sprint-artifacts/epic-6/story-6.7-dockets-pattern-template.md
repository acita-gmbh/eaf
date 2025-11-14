# Story 6.7: Dockets Pattern BPMN Template

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007

---

## User Story

As a framework developer,
I want a "Dockets Pattern" BPMN template for common migration scenarios,
So that legacy Dockets workflows can be migrated to Flowable systematically.

---

## Acceptance Criteria

1. ✅ dockets-pattern.bpmn20.xml template created in framework/workflow/src/main/resources/processes/
2. ✅ Template includes: PRESCRIPT (Ansible), Main Task (Axon command), POSTSCRIPT (Ansible), Error Handler (compensating transaction)
3. ✅ Template documented with migration guide
4. ✅ Example migration: legacy Dockets workflow → Flowable using template
5. ✅ Template tested with sample workflow
6. ✅ Migration patterns documented in docs/how-to/migrate-dockets-workflows.md
7. ✅ Template parameterizable (replace placeholders with actual commands/playbooks)

---

## Prerequisites

**Story 6.6** - Ansible Adapter

---

## References

- PRD: FR007 (Dockets migration)
- Tech Spec: Section 3 (FR007 - Dockets Pattern template)
