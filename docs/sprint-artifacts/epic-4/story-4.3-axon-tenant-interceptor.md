# Story 4.3: Axon Command Interceptor - Layer 2 Tenant Validation

**Epic:** Epic 4 - Multi-Tenancy & Data Isolation
**Status:** TODO
**Related Requirements:** FR004

---

## User Story

As a framework developer,
I want Axon command interceptor that validates tenant context matches aggregate,
So that commands cannot modify aggregates from other tenants (Layer 2).

---

## Acceptance Criteria

1. ✅ TenantValidationInterceptor.kt implements CommandHandlerInterceptor
2. ✅ Interceptor validates: TenantContext.get() matches command.tenantId
3. ✅ All commands must include tenantId field
4. ✅ Mismatch rejects command with TenantIsolationException
5. ✅ Missing context rejects command (fail-closed)
6. ✅ Integration test validates: tenant A cannot modify tenant B aggregates
7. ✅ Validation metrics: tenant_validation_failures, tenant_mismatch_attempts

---

## Prerequisites

**Story 4.2** - TenantContextFilter

---

## References

- PRD: FR004
- Architecture: Section 16 (Layer 2: Service Validation)
- Tech Spec: Section 7.2
