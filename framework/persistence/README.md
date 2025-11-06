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

## Dependencies

- **jOOQ 3.20.8** - Type-safe SQL DSL
- **PostgreSQL 42.7.8** - JDBC driver
- **Flyway 11.15.0** - Database migrations
- **Spring Boot 3.5.7** - Framework integration

## Architecture

- **Module:** `framework/persistence`
- **Package:** `com.axians.eaf.framework.persistence.projection`
- **Generated Code:** `build/generated-src/jooq/main/`
- **Integration Tests:** Uses Testcontainers PostgreSQL 16.10 (H2 forbidden)

## References

- PRD: FR003 (Event Store - jOOQ projections)
- Architecture: Section 8 (jOOQ Integration)
- Tech Spec: Epic 2 - Walking Skeleton
