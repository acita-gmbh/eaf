-- ============================================================================
-- Flyway Migration V004: Spring Modulith Event Publication Registry
-- ============================================================================
-- Creates the event_publication table required by Spring Modulith for
-- tracking published domain events across listeners.
--
-- Spring Modulith provides transactional event publication with completion
-- tracking to ensure reliable event processing across module boundaries.
--
-- Reference: Spring Modulith 1.4.4 Event Publication Registry
-- ============================================================================

-- ============================================================================
-- Event Publication Table
-- ============================================================================
-- Tracks published domain events and their completion status per listener.
-- Ensures exactly-once processing semantics for cross-module events.
CREATE TABLE event_publication (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listener_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP NOT NULL DEFAULT NOW(),
    completion_date TIMESTAMP
);

-- ============================================================================
-- Indexes for Event Publication Query Patterns
-- ============================================================================
-- Index for finding incomplete events (WHERE completion_date IS NULL)
CREATE INDEX idx_event_publication_completion ON event_publication (completion_date)
    WHERE completion_date IS NULL;

-- Index for finding events by listener
CREATE INDEX idx_event_publication_listener ON event_publication (listener_id);

-- Index for time-based queries (publication ordering)
CREATE INDEX idx_event_publication_date ON event_publication (publication_date);

-- ============================================================================
-- Comments for Documentation
-- ============================================================================
COMMENT ON TABLE event_publication IS 'Spring Modulith event publication registry for transactional event processing';
COMMENT ON COLUMN event_publication.id IS 'Unique identifier for this event publication record';
COMMENT ON COLUMN event_publication.listener_id IS 'Identifier of the listener that should process this event';
COMMENT ON COLUMN event_publication.event_type IS 'Fully qualified class name of the event type';
COMMENT ON COLUMN event_publication.serialized_event IS 'JSON-serialized event payload';
COMMENT ON COLUMN event_publication.publication_date IS 'Timestamp when the event was published';
COMMENT ON COLUMN event_publication.completion_date IS 'Timestamp when processing completed (NULL if pending)';
