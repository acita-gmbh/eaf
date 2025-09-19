-- Enterprise Application Framework event store baseline
CREATE SCHEMA IF NOT EXISTS eaf_event;
CREATE TABLE IF NOT EXISTS eaf_event.outbox (
    id UUID PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
