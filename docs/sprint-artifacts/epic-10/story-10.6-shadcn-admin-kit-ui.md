# Story 10.6: shadcn-admin-kit Operator Portal for Widgets

**Epic:** Epic 10 - Reference Application for MVP Validation
**Status:** TODO
**Related Requirements:** FR002 (UI generation), UI Design Goals from PRD

---

## User Story

As a framework developer,
I want shadcn-admin-kit UI for Widget management,
So that the reference app demonstrates frontend integration.

---

## Acceptance Criteria

1. ✅ apps/admin/ enhanced with Widget resources
2. ✅ Components: WidgetList.tsx, WidgetEdit.tsx, WidgetCreate.tsx, WidgetShow.tsx
3. ✅ shadcn/ui components used (Tables, Forms, Buttons, Dialogs)
4. ✅ React-Admin data provider integration (cursor pagination)
5. ✅ Keycloak authentication flow working
6. ✅ Role-based UI (WIDGET_ADMIN sees all actions, WIDGET_VIEWER read-only)
7. ✅ UI tested in browser with all CRUD operations
8. ✅ UI passes accessibility checks (WCAG 2.1 Level A)

---

## Prerequisites

**Story 10.3**

---

## References

- PRD: FR002, UI Design Goals (shadcn-admin-kit, WCAG 2.1 Level A)
- Architecture: Section 8 (shadcn-admin-kit Integration)
- Tech Spec: Section 6.4 (shadcn-admin-kit ↔ EAF)
