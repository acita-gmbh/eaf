# Story 10.5: Multi-Tenant Test Data and Scenarios

**Epic:** Epic 10 - Reference Application for MVP Validation
**Status:** TODO
**Related Requirements:** FR004

---

## User Story

As a framework developer,
I want test data for multiple tenants,
So that the reference app demonstrates and validates multi-tenancy.

---

## Acceptance Criteria

1. ✅ Test data seed script creates 3 tenants: tenant-a, tenant-b, tenant-c
2. ✅ Keycloak users created per tenant with different roles
3. ✅ Sample widgets created for each tenant (5 widgets per tenant)
4. ✅ Integration test validates: tenant A sees only their 5 widgets
5. ✅ Cross-tenant access scenarios tested (all blocked)
6. ✅ Test data documented and reproducible
7. ✅ Seed data script: ./scripts/seed-widget-demo.sh

---

## Prerequisites

**Story 10.1**

---

## References

- PRD: FR004
- Tech Spec: Section 7.2 (3-Layer Multi-Tenancy)
