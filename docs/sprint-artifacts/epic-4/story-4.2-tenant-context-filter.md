# Story 4.2: TenantContextFilter - Layer 1 Tenant Extraction

**Epic:** Epic 4 - Multi-Tenancy & Data Isolation
**Status:** TODO
**Related Requirements:** FR004

---

## User Story

As a framework developer,
I want a servlet filter that extracts tenant_id from JWT and populates TenantContext,
So that tenant context is available for all subsequent processing (Layer 1).

---

## Acceptance Criteria

1. ✅ TenantContextFilter.kt created as @Component with @Order(Ordered.HIGHEST_PRECEDENCE + 10)
2. ✅ Filter extracts tenant_id from JWT claim (after JWT validation in Epic 3)
3. ✅ TenantContext.set(tenantId) populates ThreadLocal
4. ✅ Missing tenant_id claim rejects request with 400 Bad Request
5. ✅ Filter ensures cleanup in finally block (TenantContext.clear())
6. ✅ Integration test validates tenant extraction from real Keycloak JWT
7. ✅ Metrics emitted: tenant_context_extraction_duration, missing_tenant_failures

---

## Prerequisites

**Story 4.1**, **Epic 3 complete**

---

## References

- PRD: FR004
- Architecture: Section 16 (Layer 1: JWT Extraction)
- Tech Spec: Section 7.2
