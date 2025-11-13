# Story 7.4: Aggregate Template (Commands, Events, Handlers)

**Epic:** Epic 7 - Scaffolding CLI & Developer Tooling
**Status:** TODO
**Related Requirements:** FR002

---

## User Story

As a framework developer,
I want Mustache templates for complete aggregate vertical slices,
So that scaffold aggregate command can generate all required components.

---

## Acceptance Criteria

1. ✅ Templates created in tools/eaf-cli/src/main/resources/templates/aggregate/:
   - Aggregate.kt.mustache (aggregate root with @AggregateIdentifier)
   - Command.kt.mustache (command DTOs)
   - Event.kt.mustache (event DTOs)
   - AggregateTest.kt.mustache (Axon Test Fixtures)
2. ✅ Templates include: package declarations, imports, KDoc, tenant_id fields
3. ✅ Generated code follows naming patterns from architecture.md
4. ✅ Templates tested with sample data
5. ✅ Generated code passes ktlint and Detekt immediately
6. ✅ Templates documented with variable reference

---

## Prerequisites

**Story 7.2**

---

## References

- PRD: FR002
- Architecture: Section 12 (Implementation Patterns - Naming)
- Tech Spec: Section 3 (FR002)
