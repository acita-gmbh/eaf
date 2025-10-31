# Story 2.6: jOOQ Configuration and Projection Tables

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store - jOOQ projections)

---

## User Story

As a framework developer,
I want jOOQ configured for type-safe SQL queries on projection tables,
So that read models are queryable with compile-time safety.

---

## Acceptance Criteria

1. ✅ jOOQ 3.20.8 dependency added to framework/persistence
2. ✅ JooqConfiguration.kt configures DSLContext bean
3. ✅ jOOQ code generation configured in Gradle (generates classes from DB schema)
4. ✅ Flyway migration V100__widget_projections.sql creates widget_view table
5. ✅ jOOQ generated classes available for widget_view table
6. ✅ Type-safe query example documented
7. ✅ Integration test validates jOOQ query execution
8. ✅ ./gradlew generateJooq generates code successfully

---

## Prerequisites

**Story 2.3** - Event Store Partitioning (PostgreSQL ready)

---

## Technical Notes

### jOOQ Configuration

**framework/persistence/src/main/kotlin/com/axians/eaf/framework/persistence/projection/JooqConfiguration.kt:**
```kotlin
@Configuration
class JooqConfiguration {

    @Bean
    fun dslContext(dataSource: DataSource): DSLContext {
        return DSL.using(dataSource, SQLDialect.POSTGRES)
    }
}
```

### jOOQ Code Generation (Gradle)

**framework/persistence/build.gradle.kts:**
```kotlin
plugins {
    id("nu.studer.jooq") version "9.0"
}

jooq {
    version.set("3.20.8")

    configurations {
        create("main") {
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/eaf"
                    user = "eaf_user"
                    password = "eaf_pass"
                }

                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        includes = ".*_view"  // Only projection tables
                    }

                    target.apply {
                        packageName = "com.axians.eaf.framework.persistence.jooq"
                        directory = "build/generated-src/jooq/main"
                    }
                }
            }
        }
    }
}
```

### Widget Projection Table Migration

**products/widget-demo/src/main/resources/db/migration/V100__widget_projections.sql:**
```sql
-- Widget Read Model (Projection)
CREATE TABLE widget_view (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for queries
CREATE INDEX idx_widget_created ON widget_view (created_at DESC);
CREATE INDEX idx_widget_published ON widget_view (published);
```

**Note:** V100+ reserved for product-specific migrations (framework uses V001-V099)

### Type-Safe Query Example

```kotlin
class WidgetQueryService(private val dsl: DSLContext) {

    fun findById(id: UUID): WidgetProjection? {
        return dsl.selectFrom(WIDGET_VIEW)
            .where(WIDGET_VIEW.ID.eq(id))
            .fetchOneInto(WidgetProjection::class.java)
    }

    fun listAll(): List<WidgetProjection> {
        return dsl.selectFrom(WIDGET_VIEW)
            .orderBy(WIDGET_VIEW.CREATED_AT.desc())
            .fetchInto(WidgetProjection::class.java)
    }
}
```

---

## Implementation Checklist

- [ ] Add jOOQ 3.20.8 to version catalog
- [ ] Add nu.studer.jooq plugin to framework/persistence
- [ ] Configure jOOQ code generation in build.gradle.kts
- [ ] Create JooqConfiguration.kt with DSLContext bean
- [ ] Create V100__widget_projections.sql migration
- [ ] Run migration: `./gradlew flywayMigrate`
- [ ] Generate jOOQ code: `./gradlew generateJooq`
- [ ] Verify generated classes in build/generated-src/jooq/
- [ ] Write integration test with type-safe queries
- [ ] Document jOOQ usage in README
- [ ] Commit: "Add jOOQ configuration and Widget projection table"

---

## Test Evidence

- [ ] jOOQ code generation succeeds
- [ ] Generated WIDGET_VIEW table class available
- [ ] Type-safe queries compile
- [ ] Integration test executes jOOQ queries successfully
- [ ] widget_view table exists in PostgreSQL

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] jOOQ code generation works
- [ ] Integration test passes
- [ ] Type-safe queries validated
- [ ] Migration successful
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.5 - Demo Widget Aggregate
**Next Story:** Story 2.7 - Widget Projection Event Handler

---

## References

- PRD: FR003 (Event Store - jOOQ projections)
- Architecture: Section 7 (jOOQ 3.20.8), Section 14 (Projection Schema Design)
- Tech Spec: Section 3 (FR003 - jOOQ), Section 4.2 (Projection Schema)
