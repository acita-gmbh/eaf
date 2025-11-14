# Story 6.6: Ansible Adapter for Legacy Migration

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007

---

## User Story

As a framework developer,
I want an Ansible adapter allowing BPMN service tasks to execute Ansible playbooks,
So that legacy Dockets automation can be migrated to Flowable.

---

## Acceptance Criteria

1. ✅ AnsibleAdapter.kt implements JavaDelegate
2. ✅ JSch 0.2.18 dependency for SSH execution
3. ✅ Adapter executes Ansible playbooks from BPMN with process variable parameters
4. ✅ Playbook execution results captured and stored in process variables
5. ✅ Error handling: playbook failures trigger BPMN error events
6. ✅ Integration test executes sample Ansible playbook from BPMN
7. ✅ Security: SSH keys and credentials managed securely (not in process variables)
8. ✅ Ansible adapter usage documented

---

## Prerequisites

**Story 6.3** - Axon Command Gateway Delegate

---

## References

- PRD: FR007 (Ansible adapter for Dockets migration)
- Tech Spec: Section 3 (FR007)
