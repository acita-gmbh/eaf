-- EAF PostgreSQL Initialization Script
-- This script is idempotent and can be run multiple times safely
-- Executed automatically on container first start via docker-entrypoint-initdb.d

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- For full-text search support

-- Create EAF schema
CREATE SCHEMA IF NOT EXISTS eaf;

-- Grant schema permissions to eaf_user (the application user)
GRANT ALL PRIVILEGES ON SCHEMA eaf TO eaf_user;
GRANT USAGE ON SCHEMA eaf TO eaf_user;
GRANT CREATE ON SCHEMA eaf TO eaf_user;

-- Set default schema search path
ALTER DATABASE eaf SET search_path TO eaf, public;

-- Grant all privileges on all tables in eaf schema to eaf_user
-- Note: Tables will be created by Flyway migrations, not by this init script
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA eaf TO eaf_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA eaf TO eaf_user;

-- Set default privileges for future tables (for Flyway migrations)
ALTER DEFAULT PRIVILEGES IN SCHEMA eaf GRANT ALL PRIVILEGES ON TABLES TO eaf_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA eaf GRANT ALL PRIVILEGES ON SEQUENCES TO eaf_user;

-- Verify setup
DO $$
BEGIN
    RAISE NOTICE 'EAF PostgreSQL initialization completed successfully';
    RAISE NOTICE 'Schema: eaf';
    RAISE NOTICE 'Extensions: uuid-ossp, pg_trgm';
    RAISE NOTICE 'Permissions granted to eaf_user';
    RAISE NOTICE 'Tables will be created by Flyway migrations';
END $$;
