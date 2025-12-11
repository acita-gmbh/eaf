# Docker Compose & jOOQ Setup

> Referenced from CLAUDE.md - Read when working on database, Docker, or jOOQ code.

## Docker Compose Structure

```text
docker/
├── eaf/                    # EAF infrastructure (PostgreSQL 16 + Keycloak 24.0.1)
│   └── docker-compose.yml
└── dvmm/                   # DVMM services (includes EAF, adds backend + frontend)
    ├── docker-compose.yml
    └── Dockerfile.backend
```

## Quick Start

```bash
# 1. Build backend JAR
./gradlew :dvmm:dvmm-app:bootJar -x test

# 2. Start everything
docker compose -f docker/dvmm/docker-compose.yml up -d

# 3. Run E2E tests
cd dvmm/dvmm-web && npm run test:e2e

# 4. Stop
docker compose -f docker/dvmm/docker-compose.yml down -v
```

## Development Mode (Backend on Host)

```bash
# Start only infrastructure
docker compose -f docker/dvmm/docker-compose.yml up postgres keycloak -d

# Run backend with debugger
./gradlew :dvmm:dvmm-app:bootRun

# Run frontend
cd dvmm/dvmm-web && npm run dev
```

## Service Ports

| Service    | Port | URL                                          |
|------------|------|----------------------------------------------|
| PostgreSQL | 5432 | `jdbc:postgresql://localhost:5432/eaf_test`  |
| Keycloak   | 8180 | `http://localhost:8180`                      |
| Backend    | 8080 | `http://localhost:8080`                      |
| Frontend   | 5173 | `http://localhost:5173`                      |

**Credentials:** PostgreSQL: `eaf/eaf`, Keycloak Admin: `admin/admin`

---

## jOOQ Code Generation

jOOQ generates type-safe Kotlin via **Testcontainers + Flyway** (real PostgreSQL schema).

### Regenerate

```bash
./gradlew :dvmm:dvmm-infrastructure:generateJooqWithTestcontainers
# Or just build - happens automatically:
./gradlew :dvmm:dvmm-infrastructure:compileKotlin
```

### Adding New Tables

1. Add migration to `eaf/eaf-eventsourcing/.../db/migration/` or `dvmm/dvmm-infrastructure/.../db/migration/`
2. Use quoted uppercase identifiers: `CREATE TABLE "DOMAIN_EVENTS"`
3. Include RLS policies with **both `USING` AND `WITH CHECK`**:

```sql
ALTER TABLE "MY_TABLE" ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "MY_TABLE"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
ALTER TABLE "MY_TABLE" FORCE ROW LEVEL SECURITY;
```

**CRITICAL:** Without `WITH CHECK`, RLS only filters reads but allows cross-tenant writes.

### FK Constraints in Tests

```kotlin
// Create parent first
private fun insertTestTimelineEvent(requestId: UUID, tenantId: TenantId) {
    insertParentRequest(id = requestId, tenantId = tenantId)
    // Then insert child...
}

// Idempotent parent insert
private fun insertParentRequest(id: UUID, tenantId: TenantId) {
    """INSERT INTO ... VALUES (...) ON CONFLICT ("ID") DO NOTHING"""
}

// Cleanup with CASCADE
@AfterEach
fun cleanup() {
    superuserDsl.execute("""TRUNCATE TABLE "PARENT_TABLE" CASCADE""")
}
```

### PostgreSQL Type Mappings

| PostgreSQL   | jOOQ Type                   | Usage                              |
|--------------|-----------------------------|------------------------------------|
| `JSONB`      | `org.jooq.JSONB`            | `JSONB.jsonb(str)`, `.data()`      |
| `UUID`       | `java.util.UUID`            | Direct                             |
| `TIMESTAMPTZ`| `java.time.OffsetDateTime`  | Direct                             |
| `BYTEA`      | `byte[]`                    | Direct                             |
