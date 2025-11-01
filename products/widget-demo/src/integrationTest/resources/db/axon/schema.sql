CREATE SEQUENCE IF NOT EXISTS domain_event_entry_seq
    INCREMENT BY 50
    START WITH 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

CREATE TABLE IF NOT EXISTS domain_event_entry (
    global_index         BIGINT PRIMARY KEY,
    event_identifier     VARCHAR(255) NOT NULL,
    meta_data            OID,
    payload              OID          NOT NULL,
    payload_revision     VARCHAR(255),
    payload_type         VARCHAR(255) NOT NULL,
    time_stamp           VARCHAR(50)  NOT NULL,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number      BIGINT       NOT NULL,
    type                 VARCHAR(255),
    CONSTRAINT uk_domain_event_entry UNIQUE (aggregate_identifier, sequence_number),
    CONSTRAINT uk_domain_event_entry_event_identifier UNIQUE (event_identifier)
);

CREATE SEQUENCE IF NOT EXISTS snapshot_event_entry_seq
    INCREMENT BY 50
    START WITH 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

CREATE TABLE IF NOT EXISTS snapshot_event_entry (
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number      BIGINT       NOT NULL,
    type                 VARCHAR(255) NOT NULL,
    event_identifier     VARCHAR(255) NOT NULL,
    meta_data            OID,
    payload              OID          NOT NULL,
    payload_revision     VARCHAR(255),
    payload_type         VARCHAR(255) NOT NULL,
    time_stamp           VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_snapshot_event_entry PRIMARY KEY (aggregate_identifier, sequence_number),
    CONSTRAINT uk_snapshot_event_entry_event_identifier UNIQUE (event_identifier)
);

CREATE TABLE IF NOT EXISTS token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment        INTEGER      NOT NULL,
    owner          VARCHAR(255),
    timestamp      VARCHAR(255) NOT NULL,
    token          OID,
    token_type     VARCHAR(255),
    CONSTRAINT pk_token_entry PRIMARY KEY (processor_name, segment)
);

CREATE TABLE IF NOT EXISTS saga_entry (
    saga_id        VARCHAR(255) NOT NULL,
    revision       VARCHAR(255),
    saga_type      VARCHAR(255) NOT NULL,
    serialized_saga OID,
    CONSTRAINT pk_saga_entry PRIMARY KEY (saga_id)
);

CREATE TABLE IF NOT EXISTS association_value_entry (
    id                BIGSERIAL PRIMARY KEY,
    association_key   VARCHAR(255) NOT NULL,
    association_value VARCHAR(255) NOT NULL,
    saga_id           VARCHAR(255) NOT NULL,
    saga_type         VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_assoc_value_entry
    ON association_value_entry (association_key, association_value, saga_type, saga_id);

CREATE INDEX IF NOT EXISTS idx_assoc_value_entry
    ON association_value_entry (association_value);

CREATE INDEX IF NOT EXISTS idx_assoc_key_entry
    ON association_value_entry (association_key);

CREATE INDEX IF NOT EXISTS idx_token_entry_processor_owner
    ON token_entry (processor_name, owner);

ALTER SEQUENCE domain_event_entry_seq OWNED BY domain_event_entry.global_index;
ALTER TABLE domain_event_entry ALTER COLUMN global_index SET DEFAULT nextval('domain_event_entry_seq');

-- Widget Projection Read Model Table (jOOQ-based)
CREATE TABLE IF NOT EXISTS widget_projection (
    widget_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    value NUMERIC(19, 2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_widget_projection_tenant_id
    ON widget_projection (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_widget_projection_category
    ON widget_projection (tenant_id, category, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_widget_projection_value_desc
    ON widget_projection (tenant_id, value DESC);

CREATE INDEX IF NOT EXISTS idx_widget_projection_created_at
    ON widget_projection (tenant_id, created_at DESC);

-- NOTE: Row-Level Security (RLS) intentionally OMITTED from test schema
-- Production schema (scripts/sql/widget_projection_schema.sql) has proper RLS policies
-- Test schema bypasses RLS to allow test infrastructure operations without tenant context
-- This is a standard testing pattern - tests need flexibility for setup/teardown/validation
