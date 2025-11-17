# PostgreSQL Row-Level Security (RLS) for Multi-Tenancy

**Epic 4, Story 4.4**
**Last Updated:** 2025-11-17
**Status:** Production-Ready

---

## Overview

PostgreSQL Row-Level Security (RLS) provides **Layer 3** of the EAF 3-layer tenant isolation defense. RLS enforces tenant boundaries at the database kernel level, providing protection even against SQL injection attacks or application layer bugs.

**Defense-in-Depth Architecture:**
- **Layer 1:** TenantContextFilter (Story 4.2) - Extract tenant_id from JWT
- **Layer 2:** TenantValidationInterceptor (Story 4.3) - Validate commands at service layer
- **Layer 3:** PostgreSQL RLS (This Document) - Database-level enforcement

---

## How RLS Works

### Session Variable Propagation

```kotlin
// Application Flow:
1. HTTP Request → JWT with tenant_id claim
2. TenantContextFilter extracts tenant_id → TenantContext (ThreadLocal)
3. jOOQ Query executes → TenantContextExecuteListener.start()
4. ExecuteListener sets: SET LOCAL app.tenant_id = '<tenant-id>'
5. PostgreSQL RLS policies filter queries: WHERE tenant_id = current_setting('app.tenant_id')
```

### RLS Policy Enforcement

```sql
-- Example: SELECT * FROM widget_projection
-- PostgreSQL automatically rewrites to:
SELECT * FROM widget_projection
WHERE tenant_id = current_setting('app.tenant_id', true)

-- Result: Only rows matching current tenant are visible
-- Even SQL injection cannot bypass this filter (kernel-level enforcement)
```

---

## Production Configuration

### Framework Migration: V004__rls_policies.sql

Located: `framework/persistence/src/main/resources/db/migration/V004__rls_policies.sql`

**Purpose:** Framework-level RLS infrastructure (helper functions, patterns)

**Contents:**
- Helper function: `get_current_tenant_id()` (returns NULL if not set - fail-safe)
- Documentation and patterns for product migrations
- Reusable RLS policy templates

### Product Migration: V101__widget_projection_multi_tenancy.sql

Located: `products/widget-demo/src/main/resources/db/migration/V101__widget_projection_multi_tenancy.sql`

**Purpose:** Add multi-tenancy support to widget_projection table

**Contents:**
```sql
-- 1. Add tenant_id column
ALTER TABLE widget_projection
    ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default-tenant';

ALTER TABLE widget_projection
    ALTER COLUMN tenant_id DROP DEFAULT;

-- 2. Create indexes
CREATE INDEX idx_widget_projection_tenant_id ON widget_projection (tenant_id);
CREATE INDEX idx_widget_projection_tenant_created ON widget_projection (tenant_id, created_at DESC);

-- 3. Enable RLS
ALTER TABLE widget_projection ENABLE ROW LEVEL SECURITY;

-- 4. Create tenant isolation policy
CREATE POLICY tenant_isolation ON widget_projection
    FOR ALL
    USING (tenant_id = get_current_tenant_id());

-- 5. Validation checks
-- (Validates RLS enabled and policy created)
```

---

## jOOQ Integration

### TenantContextExecuteListener

Located: `framework/multi-tenancy/src/main/kotlin/com/axians/eaf/framework/multitenancy/TenantContextExecuteListener.kt`

**Purpose:** Sets PostgreSQL session variable before each jOOQ query

**Implementation:**
```kotlin
@Component
class TenantContextExecuteListener : ExecuteListener {
    override fun start(ctx: ExecuteContext) {
        val tenantId = TenantContext.current() // nullable

        if (tenantId != null) {
            ctx.dsl().execute("SET LOCAL app.tenant_id = ?", tenantId)
        }
        // If null: RLS returns empty (fail-safe)
    }
}
```

**Key Properties:**
- ✅ Uses `TenantContext.current()` (nullable) - safe for system queries
- ✅ `SET LOCAL` scope - transaction-only (auto-cleanup)
- ✅ Fail-safe - missing tenant → empty result (no cross-tenant leak)
- ✅ Thread-safe - each connection has its own transaction

### JooqConfiguration

Located: `framework/persistence/src/main/kotlin/com/axians/eaf/framework/persistence/projection/JooqConfiguration.kt`

**Purpose:** Registers ExecuteListeners with jOOQ DSLContext

**Implementation:**
```kotlin
@Bean
@Suppress("SpreadOperator") // Acceptable: Bean init only
fun dslContext(
    dataSource: DataSource,
    executeListeners: List<ExecuteListener>
): DSLContext {
    val configuration = DefaultConfiguration()
        .set(dataSource)
        .set(SQLDialect.POSTGRES)
        .set(*executeListeners.toTypedArray())

    return DSL.using(configuration)
}
```

---

## Security Properties

### Fail-Safe Design

1. **Missing Tenant Context:**
   - TenantContextExecuteListener skips session variable
   - RLS policy: `tenant_id = get_current_tenant_id()` returns NULL
   - Result: **Empty result set** (no data leak)

2. **SQL Injection Protection:**
   - RLS policies enforced at PostgreSQL kernel level
   - Even `' OR '1'='1` injection cannot bypass tenant filter
   - Database rejects cross-tenant access regardless of application bugs

3. **Generic Error Messages:**
   - No tenant ID values exposed in errors (CWE-209 protection)
   - Failed queries return empty (no enumeration capability)

---

## Performance

### Targets

- **Session Variable Overhead:** <0.1ms per query (SET LOCAL)
- **RLS Policy Overhead:** <2ms per query (with proper indexing)
- **Total Impact:** <2ms per tenant-scoped query

### Optimization

**BTREE Indexes** (current implementation):
```sql
CREATE INDEX idx_widget_projection_tenant_id ON widget_projection (tenant_id);
```
- Efficient for small-medium datasets (<100k rows)
- Fast tenant filtering with O(log n) lookup

**BRIN Indexes** (future optimization if needed):
```sql
CREATE INDEX idx_widget_projection_tenant_id ON widget_projection
    USING BRIN (tenant_id);
```
- Optimal for large datasets (>1M rows) if tenant IDs cluster by time
- Minimal storage overhead
- Consider if performance testing shows benefit

---

## Testing Strategy

### Production Migration Testing

Production migrations (V004, V101) are validated in Story 4.6-4.7:
- **Story 4.6:** Widget aggregate multi-tenancy integration
- **Story 4.7:** Comprehensive tenant isolation tests with RLS validation

### Test Schema

Test schema (`src/integration-test/resources/schema.sql`) intentionally does NOT include RLS:
- ✅ Keeps test setup simple and fast
- ✅ Avoids test complexity with session variables
- ✅ Tests focus on business logic, not database security

**RLS Validation:** Comprehensive RLS testing in Story 4.7 with Testcontainers + Flyway migrations.

---

## Usage Examples

### Query Execution (Automatic)

```kotlin
// Application code - no RLS awareness needed!
val widgets = widgetQueryHandler.findByTenant()

// Execution flow:
// 1. TenantContext has "tenant-a" (set by Layer 1 filter)
// 2. TenantContextExecuteListener sets: app.tenant_id = 'tenant-a'
// 3. jOOQ executes: SELECT * FROM widget_projection
// 4. PostgreSQL rewrites: SELECT * FROM widget_projection
//                          WHERE tenant_id = 'tenant-a'
// 5. Only tenant-a widgets returned
```

### Manual Session Variable (Testing)

```kotlin
// Direct JDBC for RLS testing (Story 4.7)
connection.createStatement().execute("SET LOCAL app.tenant_id = 'tenant-a'")
val rs = connection.createStatement().executeQuery("SELECT * FROM widget_projection")
// Only tenant-a widgets returned (RLS enforced)
```

---

## Adding RLS to New Tables

### Pattern for Product Migrations

```sql
-- 1. Add tenant_id column
ALTER TABLE your_table
    ADD COLUMN tenant_id VARCHAR(64) NOT NULL;

-- 2. Create indexes
CREATE INDEX idx_your_table_tenant_id ON your_table (tenant_id);

-- 3. Enable RLS
ALTER TABLE your_table ENABLE ROW LEVEL SECURITY;

-- 4. Create tenant isolation policy
CREATE POLICY tenant_isolation ON your_table
    FOR ALL
    USING (tenant_id = get_current_tenant_id());
```

### Validation Checks

```sql
-- Verify RLS enabled
SELECT relrowsecurity FROM pg_class WHERE relname = 'your_table';
-- Expected: true

-- Verify policy exists
SELECT * FROM pg_policies WHERE tablename = 'your_table';
-- Expected: 1 row with policyname='tenant_isolation'
```

---

## Troubleshooting

### RLS Returns Empty When It Shouldn't

**Cause:** Session variable not set (TenantContext missing)

**Debug:**
```sql
-- Check current session variable
SELECT current_setting('app.tenant_id', true);
-- Expected: '<tenant-id>' or NULL
```

**Fix:** Ensure TenantContext is set by Layer 1 filter before queries

### Performance Issues

**Symptom:** Queries >2ms slower with RLS

**Debug:**
1. Check index usage: `EXPLAIN ANALYZE SELECT * FROM widget_projection`
2. Verify tenant_id index exists: `\d widget_projection`
3. Analyze table statistics: `ANALYZE widget_projection`

**Fix:** Add/optimize tenant_id index (BTREE → BRIN for large datasets)

---

## References

- **Architecture:** Section 16 - Multi-Tenancy (3-Layer Defense)
- **PRD:** FR004 - Multi-Tenancy Requirements
- **PostgreSQL Docs:** [Row Security Policies](https://www.postgresql.org/docs/16/ddl-rowsecurity.html)
- **Story 4.7:** Comprehensive RLS Integration Testing
