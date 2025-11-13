# Story 7.8: scaffold ra-resource Command (shadcn-admin-kit UI)

**Epic:** Epic 7 - Scaffolding CLI & Developer Tooling
**Status:** TODO
**Related Requirements:** FR002

---

## User Story

As a framework developer,
I want `eaf scaffold ra-resource <name>` command to generate shadcn-admin-kit UI components,
So that I can create operator portal pages for CRUD operations.

---

## Acceptance Criteria

1. ✅ ScaffoldRaResourceCommand.kt implements UI scaffolding
2. ✅ Templates in templates/ra-resource/: List.tsx.mustache, Edit.tsx.mustache, Create.tsx.mustache, Show.tsx.mustache
3. ✅ Generated components use shadcn/ui primitives and react-admin hooks
4. ✅ Generated components connect to REST API via data provider
5. ✅ TypeScript interfaces generated for API DTOs
6. ✅ Generated code passes ESLint and TypeScript checks
7. ✅ Integration test: scaffold ra-resource Widget → compiles → renders in browser
8. ✅ Command usage: `eaf scaffold ra-resource Widget`

---

## Prerequisites

**Story 7.6**

---

## References

- PRD: FR002, UI Design Goals (shadcn-admin-kit)
- Architecture: Section 8 (shadcn-admin-kit Integration)
- Tech Spec: Section 6.4 (shadcn-admin-kit ↔ EAF REST API)
