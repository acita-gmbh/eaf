# Story 7.6: scaffold api-resource Command

**Epic:** Epic 7 - Scaffolding CLI & Developer Tooling
**Status:** TODO
**Related Requirements:** FR002

---

## User Story

As a framework developer,
I want `eaf scaffold api-resource <name>` command to generate REST controllers,
So that I can expose aggregates via HTTP API with OpenAPI documentation.

---

## Acceptance Criteria

1. ✅ ScaffoldApiResourceCommand.kt implements API scaffolding
2. ✅ Templates: Controller.kt.mustache, Request.kt.mustache, Response.kt.mustache
3. ✅ Generated controller includes: CRUD endpoints, CommandGateway/QueryGateway usage, @OpenAPI annotations
4. ✅ Request/Response DTOs with validation annotations
5. ✅ Generated code compiles and passes quality gates
6. ✅ Integration test: scaffold api-resource Widget → compiles → Swagger UI shows endpoints
7. ✅ Generated endpoints follow REST conventions (POST, GET, PUT, DELETE)
8. ✅ Command usage: `eaf scaffold api-resource Widget`

---

## Prerequisites

**Story 7.4**

---

## References

- PRD: FR002
- Tech Spec: Section 3 (FR002 - scaffold api-resource)
