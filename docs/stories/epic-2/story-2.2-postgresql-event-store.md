# Story 2.2: PostgreSQL Event Store Setup with Flyway

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store with Integrity and Performance)

---

## User Story

As a framework developer,
I want PostgreSQL configured as the Axon event store using JdbcEventStorageEngine,
So that events are persisted durably in PostgreSQL.

---

## Acceptance Criteria

1. ✅ framework/persistence module created with Axon JDBC and Flyway dependencies
2. ✅ PostgresEventStoreConfiguration.kt configures JdbcEventStorageEngine
3. ✅ Flyway migration V001__event_store_schema.sql creates Axon tables (domain_event_entry, snapshot_entry, saga tables)
4. ✅ DataSource configuration for PostgreSQL in application.yml
5. ✅ Integration test verifies events can be stored and retrieved
6. ✅ Testcontainers PostgreSQL used for integration tests
7. ✅ Migration executes successfully on docker-compose PostgreSQL
8. ✅ Event store tables visible in database

---

## Prerequisites

**Story 2.1** - Axon Framework Core Configuration

---

## Technical Notes

### PostgreSQL Event Store Configuration

**framework/persistence/src/main/kotlin/com/axians/eaf/framework/persistence/eventstore/PostgresEventStoreConfiguration.kt:**
```kotlin
@Configuration
class PostgresEventStoreConfiguration {

    @Bean
    fun eventStorageEngine(dataSource: DataSource): EventStorageEngine {
        return JdbcEventStorageEngine.builder()
            .dataSource(dataSource)
            .transactionManager(NoTransactionManager.INSTANCE)
            .build()
    }
}
```

### Flyway Migration V001

**framework/persistence/src/main/resources/db/migration/V001__event_store_schema.sql:**
```sql
-- Axon Framework Event Store Tables

-- Domain Events (time-series data)
CREATE TABLE domain_event_entry (
    global_index BIGSERIAL PRIMARY KEY,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    meta_data BYTEA,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    UNIQUE (aggregate_identifier, sequence_number)
);

-- Snapshots
CREATE TABLE snapshot_entry (
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    meta_data BYTEA,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregate_identifier, sequence_number)
);

-- Saga Tables
CREATE TABLE saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    revision VARCHAR(255),
    saga_type VARCHAR(255) NOT NULL,
    serialized_saga BYTEA NOT NULL,
    PRIMARY KEY (saga_id, saga_type)
);

CREATE TABLE association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255) NOT NULL
);
```

### DataSource Configuration

**application.yml:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/eaf
    username: eaf_user
    password: eaf_pass
    driver-class-name: org.postgresql.Driver

  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## Implementation Checklist

- [ ] Create framework/persistence module
- [ ] Add Axon JDBC, Flyway, PostgreSQL driver to dependencies
- [ ] Create PostgresEventStoreConfiguration.kt
- [ ] Create V001__event_store_schema.sql in src/main/resources/db/migration/
- [ ] Configure DataSource in application.yml
- [ ] Write integration test: EventStoreIntegrationTest.kt
- [ ] Use Testcontainers PostgreSQL for tests
- [ ] Run migration on Docker Compose PostgreSQL
- [ ] Verify tables created: `psql -h localhost -U eaf_user -d eaf -c "\dt"`
- [ ] Commit: "Add PostgreSQL event store with Flyway migrations"

---

## Test Evidence

- [ ] Flyway migration executes successfully
- [ ] domain_event_entry, snapshot_entry, saga tables exist
- [ ] Integration test stores and retrieves events
- [ ] Testcontainers PostgreSQL starts correctly
- [ ] No migration errors

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Integration tests pass
- [ ] Migration tested on docker-compose PostgreSQL
- [ ] Tables visible in database
- [ ] Module documented
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.1 - Axon Framework Core Configuration
**Next Story:** Story 2.3 - Event Store Partitioning and Optimization

---

## References

- PRD: FR003 (Event Store with Integrity and Performance)
- Architecture: Section 14 (Data Architecture - Event Store Schema)
- Tech Spec: Section 3 (FR003 Implementation), Section 4.1 (Event Store Schema)
