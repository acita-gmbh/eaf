# Story 7.7: scaffold projection Command

**Epic:** Epic 7 - Scaffolding CLI & Developer Tooling
**Status:** TODO
**Related Requirements:** FR002

---

## User Story

As a framework developer,
I want `eaf scaffold projection <name>` command to generate projection event handlers,
So that I can create read models from events with type-safe jOOQ queries.

---

## Acceptance Criteria

1. ✅ ScaffoldProjectionCommand.kt implements projection scaffolding
2. ✅ Templates: Projection.kt.mustache, EventHandler.kt.mustache, migration.sql.mustache
3. ✅ Generated event handler includes @EventHandler for all aggregate events
4. ✅ Generated Flyway migration creates projection table
5. ✅ jOOQ integration configured (code generation after migration)
6. ✅ Generated code compiles after running migration
7. ✅ Integration test: scaffold projection Widget → migration → jOOQ generation → compiles
8. ✅ Command usage: `eaf scaffold projection Widget`

---

## Prerequisites

**Story 7.4**

---

## References

- PRD: FR002
- Tech Spec: Section 3 (FR002 - scaffold projection)
