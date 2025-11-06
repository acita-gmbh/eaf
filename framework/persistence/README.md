# EAF Persistence Framework

Framework module providing persistence infrastructure for CQRS/ES read models with jOOQ type-safe SQL queries.

## Features

- **jOOQ 3.20.8** - Type-safe SQL query generation for projection tables
- **Flyway Migrations** - Database schema version control
- **PostgreSQL 16.10** - Production database with optimizations
- **Code Generation** - Kotlin classes generated from database schema

## jOOQ Configuration

### DSLContext Bean

The `JooqConfiguration` class configures a Spring `DSLContext` bean for type-safe SQL queries:

```kotlin
@Configuration
class JooqConfiguration {
    @Bean
    fun dslContext(dataSource: DataSource): DSLContext {
        return DSL.using(dataSource, SQLDialect.POSTGRES)
    }
}
```

### Code Generation

jOOQ generates Kotlin classes from the PostgreSQL schema at build time:

```bash
# Generate jOOQ classes from database schema
./gradlew :framework:persistence:jooqCodegen
```

**Generated classes location:** `build/generated-src/jooq/main/`

**Configuration:**
- **Input Schema:** `eaf` (PostgreSQL schema)
- **Includes:** `.*_view` (only projection tables)
- **Generator:** `KotlinGenerator` (Kotlin-first code generation)
- **Package:** `com.axians.eaf.framework.persistence.jooq`

## Type-Safe Query Example

### Widget Projection Table (widget_view)

Projection table for CQRS read model:

```sql
CREATE TABLE widget_view (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Type-Safe Queries with jOOQ

```kotlin
import com.axians.eaf.framework.persistence.jooq.tables.WidgetView.Companion.WIDGET_VIEW
import com.axians.eaf.framework.persistence.jooq.tables.records.WidgetViewRecord
import org.jooq.DSLContext
import java.util.UUID

class WidgetQueryService(private val dsl: DSLContext) {

    // SELECT query with compile-time safety
    fun findById(id: UUID): WidgetViewRecord? {
        return dsl.selectFrom(WIDGET_VIEW)
            .where(WIDGET_VIEW.ID.eq(id))
            .fetchOne()
    }

    // Complex query with filters and ordering
    fun findPublishedWidgets(): List<WidgetViewRecord> {
        return dsl.selectFrom(WIDGET_VIEW)
            .where(WIDGET_VIEW.PUBLISHED.eq(true))
            .orderBy(WIDGET_VIEW.CREATED_AT.desc())
            .fetch()
    }

    // INSERT with type-safe columns
    fun createWidget(id: UUID, name: String): Int {
        return dsl.insertInto(WIDGET_VIEW)
            .columns(
                WIDGET_VIEW.ID,
                WIDGET_VIEW.NAME,
                WIDGET_VIEW.PUBLISHED
            )
            .values(id, name, false)
            .execute()
    }

    // UPDATE with compile-time validation
    fun publishWidget(id: UUID): Int {
        return dsl.update(WIDGET_VIEW)
            .set(WIDGET_VIEW.PUBLISHED, true)
            .set(WIDGET_VIEW.UPDATED_AT, java.time.OffsetDateTime.now())
            .where(WIDGET_VIEW.ID.eq(id))
            .execute()
    }

    // Aggregation query
    fun countPublishedWidgets(): Int {
        return dsl.selectCount()
            .from(WIDGET_VIEW)
            .where(WIDGET_VIEW.PUBLISHED.eq(true))
            .fetchOne(0, Int::class.java) ?: 0
    }
}
```

### Compile-Time Safety Benefits

✅ **Type-safe columns:** `WIDGET_VIEW.NAME` vs `WIDGET_VIEW.PUBLISHED`
✅ **Wrong column name = compilation error:** `WIDGET_VIEW.WRONG_FIELD` fails at compile-time
✅ **Type-safe values:** Cannot pass `Boolean` to `NAME` column (String expected)
✅ **Fluent DSL:** IDE autocomplete for all query operations

## Database Migrations

### Framework Migrations (V001-V099)

Reserved for EAF framework infrastructure (Event Store, etc.).

### Product Migrations (V100+)

Product-specific projection tables start at V100:

**Example:** `products/widget-demo/src/main/resources/db/migration/V100__widget_projections.sql`

```sql
CREATE TABLE widget_view (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_widget_created ON widget_view (created_at DESC);
CREATE INDEX idx_widget_published ON widget_view (published);
```

## Build Tasks

```bash
# Generate jOOQ classes from PostgreSQL schema
./gradlew :framework:persistence:jooqCodegen

# Run tests (unit + integration)
./gradlew :framework:persistence:test
./gradlew :framework:persistence:integrationTest

# Build framework module
./gradlew :framework:persistence:build
```

## Schema Change Workflow

When you add or modify projection tables (`*_view` tables), follow this workflow to regenerate jOOQ classes:

### 1. Create or Update Migration

Create a new Flyway migration in the appropriate module:

**Framework migrations (V001-V099):**
```bash
# Example: V050__add_audit_view.sql
framework/persistence/src/main/resources/db/migration/V050__add_audit_view.sql
```

**Product migrations (V100+):**
```bash
# Example: V101__add_order_view.sql
products/widget-demo/src/main/resources/db/migration/V101__add_order_view.sql
```

### 2. Apply Migration

Run the migration to create/update the table in your local database:

```bash
# Ensure PostgreSQL is running
./scripts/init-dev.sh

# Apply migrations (automatic on app startup, or manually via Spring Boot)
./gradlew :products:widget-demo:bootRun
```

### 3. Regenerate jOOQ Classes

Generate updated Kotlin classes from the new schema:

```bash
# Set database connection (or use defaults)
export JOOQ_DB_URL=jdbc:postgresql://localhost:5432/eaf
export JOOQ_DB_USER=eaf_user
export JOOQ_DB_PASSWORD=eaf_password

# Regenerate jOOQ classes
./gradlew :framework:persistence:jooqCodegen
```

**What happens:**
- jOOQ scans the `eaf` schema for tables matching `.*_view` pattern
- Generates Kotlin classes to `src/main/generated-kotlin/jooq/`
- Updates table classes (e.g., `AuditView.kt`, `OrderView.kt`)
- Preserves existing classes (e.g., `WidgetView.kt`)

### 4. Verify Generated Classes

Check that new classes were created:

```bash
# List generated table classes
ls src/main/generated-kotlin/jooq/com/axians/eaf/framework/persistence/jooq/tables/

# Expected output: AuditView.kt, OrderView.kt, WidgetView.kt, etc.
```

### 5. Commit Generated Sources

jOOQ-generated sources are committed to enable CI builds (no database at build time):

```bash
git add src/main/generated-kotlin/jooq/
git commit -m "feat: Regenerate jOOQ classes for new projection tables"
```

**Note:** Generated sources have `@file:Suppress("ktlint")` and are excluded from code style checks.

### 6. Update Tests

Add integration tests for the new projection table:

```kotlin
test("New projection table should support type-safe queries") {
    dslContext.selectFrom(AUDIT_VIEW)
        .where(AUDIT_VIEW.USER_ID.eq(userId))
        .fetch()
}
```

### Common Issues

**Problem:** `relation "eaf.my_view" does not exist`
- **Cause:** Migration not applied, or table created in wrong schema
- **Fix:** Ensure migration has `CREATE SCHEMA IF NOT EXISTS eaf; SET search_path TO eaf;`

**Problem:** `Unresolved reference: MY_VIEW`
- **Cause:** jOOQ classes not generated
- **Fix:** Run `./gradlew :framework:persistence:jooqCodegen`

**Problem:** jOOQ generates classes for wrong tables
- **Cause:** `includes` pattern too broad
- **Fix:** Verify `includes = ".*_view"` in build.gradle.kts (line 79)

## Dependencies

- **jOOQ 3.20.8** - Type-safe SQL DSL (see Version Monitoring below)
- **PostgreSQL 42.7.8** - JDBC driver
- **Flyway 11.15.0** - Database migrations
- **Spring Boot 3.5.7** - Framework integration

### Version Monitoring

**Current Version:** jOOQ 3.20.8 (verified via integration test)

**Monitoring Strategy:**
- Monitor [jOOQ Release Notes](https://www.jooq.org/notes) for version 3.21+ releases
- Focus areas for future upgrades:
  - Kotlin DSL improvements (better type inference, coroutine support)
  - Performance optimizations for code generation
  - PostgreSQL 17+ compatibility
  - Breaking changes in query API

**Upgrade Checklist (for jOOQ 3.21+):**
1. Review release notes for breaking changes
2. Update version in `gradle/libs.versions.toml`
3. Regenerate jOOQ classes: `./gradlew :framework:persistence:jooqCodegen`
4. Run full test suite: `./gradlew :framework:persistence:integrationTest`
5. Verify version test passes (JooqCodegenValidationTest)
6. Update this README with version-specific notes

## Architecture

- **Module:** `framework/persistence`
- **Package:** `com.axians.eaf.framework.persistence.projection`
- **Generated Code:** `build/generated-src/jooq/main/`
- **Integration Tests:** Uses Testcontainers PostgreSQL 16.10 (H2 forbidden)

## References

- PRD: FR003 (Event Store - jOOQ projections)
- Architecture: Section 8 (jOOQ Integration)
- Tech Spec: Epic 2 - Walking Skeleton
