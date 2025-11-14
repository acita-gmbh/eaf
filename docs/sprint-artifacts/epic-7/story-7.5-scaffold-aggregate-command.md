# Story 7.5: scaffold aggregate Command

**Epic:** Epic 7 - Scaffolding CLI & Developer Tooling
**Status:** TODO
**Related Requirements:** FR002, NFR003

---

## User Story

As a framework developer,
I want `eaf scaffold aggregate <name>` command to generate complete CQRS vertical slices,
So that I can create new aggregates in minutes with all boilerplate eliminated.

---

## Acceptance Criteria

1. ✅ ScaffoldAggregateCommand.kt implements aggregate scaffolding
2. ✅ Command parameters: aggregate name, module (default: products/), commands list (optional)
3. ✅ Generates: Aggregate.kt, Commands, Events, AggregateTest.kt using templates
4. ✅ Pluralization logic for aggregate names (Widget → Widgets)
5. ✅ Generated code compiles and tests pass immediately
6. ✅ Integration test: scaffold aggregate Order → compiles → tests pass
7. ✅ Generated aggregate follows DDD base classes from framework/core
8. ✅ Command usage: `eaf scaffold aggregate Order --commands=Create,Update,Cancel`

---

## Prerequisites

**Story 7.4**

---

## References

- PRD: FR002, NFR003 (70-80% boilerplate elimination)
- Tech Spec: Section 3 (FR002)
