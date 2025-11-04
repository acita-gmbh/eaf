-- ============================================================================
-- Flyway Migration V001: Axon Framework Event Store Schema
-- ============================================================================
-- Creates the standard Axon Framework event store tables for PostgreSQL.
-- This schema supports:
-- - Event Sourcing: DomainEventEntry stores all domain events
-- - Snapshots: SnapshotEventEntry stores aggregate snapshots (Story 2.4)
-- - Sagas: SagaEntry and AssociationValueEntry support saga orchestration
--
-- Reference: Axon Framework 4.12.1 JDBC Event Store Schema
-- Migration Strategy: Framework migrations use V001-V099 range
-- Table Names: Uses Axon standard camelCase names (PostgreSQL converts to lowercase)
-- ============================================================================

-- ============================================================================
-- Domain Events Table (Time-Series Data)
-- ============================================================================
-- Stores all domain events for event-sourced aggregates.
-- globalIndex provides total ordering across all events.
-- (aggregateIdentifier, sequenceNumber) ensures event ordering per aggregate.
CREATE TABLE DomainEventEntry (
    globalIndex BIGSERIAL PRIMARY KEY,
    aggregateIdentifier VARCHAR(255) NOT NULL,
    sequenceNumber BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    eventIdentifier VARCHAR(255) NOT NULL UNIQUE,
    metaData BYTEA,
    payload BYTEA NOT NULL,
    payloadRevision VARCHAR(255),
    payloadType VARCHAR(255) NOT NULL,
    timeStamp VARCHAR(255) NOT NULL,
    UNIQUE (aggregateIdentifier, sequenceNumber)
);

-- ============================================================================
-- Index Strategy (Story 2.3 will add BRIN indexes and partitioning)
-- ============================================================================
-- Standard B-tree indexes for aggregate event retrieval
CREATE INDEX idx_domain_event_aggregate ON DomainEventEntry(aggregateIdentifier, sequenceNumber);
CREATE INDEX idx_domain_event_timestamp ON DomainEventEntry(timeStamp);

-- ============================================================================
-- Snapshots Table
-- ============================================================================
-- Stores aggregate snapshots for performance optimization (Story 2.4).
-- Snapshots allow aggregates to be reconstructed from a known state rather
-- than replaying all events from the beginning.
CREATE TABLE SnapshotEventEntry (
    aggregateIdentifier VARCHAR(255) NOT NULL,
    sequenceNumber BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    eventIdentifier VARCHAR(255) NOT NULL UNIQUE,
    metaData BYTEA,
    payload BYTEA NOT NULL,
    payloadRevision VARCHAR(255),
    payloadType VARCHAR(255) NOT NULL,
    timeStamp VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregateIdentifier, sequenceNumber)
);

-- ============================================================================
-- Saga Tables (Workflow Orchestration - Epic 6)
-- ============================================================================
-- Saga Entry: Stores saga instance state for long-running business processes
CREATE TABLE SagaEntry (
    sagaId VARCHAR(255) NOT NULL,
    revision VARCHAR(255),
    sagaType VARCHAR(255) NOT NULL,
    serializedSaga BYTEA NOT NULL,
    PRIMARY KEY (sagaId, sagaType)
);

-- Association Value Entry: Maps correlation identifiers to saga instances
CREATE TABLE AssociationValueEntry (
    id BIGSERIAL PRIMARY KEY,
    associationKey VARCHAR(255) NOT NULL,
    associationValue VARCHAR(255),
    sagaId VARCHAR(255) NOT NULL,
    sagaType VARCHAR(255) NOT NULL
);

-- Index for efficient saga lookup by association
CREATE INDEX idx_saga_association ON AssociationValueEntry(sagaId, sagaType);
CREATE INDEX idx_saga_association_value ON AssociationValueEntry(associationKey, associationValue);

-- ============================================================================
-- Token Store Table (Event Processor Tracking)
-- ============================================================================
-- Stores tracking tokens for event processors to track their processing position
CREATE TABLE TokenEntry (
    processorName VARCHAR(255) NOT NULL,
    segment INTEGER NOT NULL,
    token BYTEA,
    tokenType VARCHAR(255),
    timestamp VARCHAR(255),
    owner VARCHAR(255),
    PRIMARY KEY (processorName, segment)
);

-- ============================================================================
-- Future Optimizations (Planned in Story 2.3)
-- ============================================================================
-- Story 2.3 will add:
-- - Monthly time-based partitioning on DomainEventEntry
-- - BRIN indexes for time-range queries (more efficient for time-series data)
-- - Performance tuning for 100K+ events
-- ============================================================================
