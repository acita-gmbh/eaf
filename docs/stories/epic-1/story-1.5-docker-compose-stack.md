# Story 1.5: Docker Compose Development Stack

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** review
**Story Points:** TBD
**Related Requirements:** FR001

## Dev Agent Record

**Context Reference:**
- [Story Context XML](./1-5-docker-compose-stack.context.xml) - Generated 2025-11-02

**Debug Log:**
- Implemented docker-compose.yml with all 5 services (PostgreSQL 16.10, Keycloak 26.4.2, Redis 7.2, Prometheus latest, Grafana 12.2.0)
- Configured health checks for all services with appropriate intervals and start periods
- Implemented service startup order using depends_on with health condition checks
- Created PostgreSQL init script (01-init.sql) with uuid-ossp extension, eaf schema, and complete Axon Framework table structure
- Created Keycloak realm-export.json with eaf realm, eaf-api client, 3 test users (admin, viewer, tenant-b-admin), and proper tenant_id claim mapper
- Created Prometheus configuration with scraping for EAF application and Keycloak metrics
- Added .env.example with all configurable ports and credentials
- Fixed Keycloak health check to use management port 9000 with /health/ready endpoint and shell-based HTTP check (curl not available in image)
- All services verified healthy with connectivity tests

**File List:**
- `docker-compose.yml` - Docker Compose configuration with 5 services
- `docker/postgres/init-scripts/01-init.sql` - PostgreSQL initialization script
- `docker/keycloak/realm-export.json` - Keycloak realm configuration
- `docker/prometheus/prometheus.yml` - Prometheus scrape configuration
- `.env.example` - Environment variable template
- `.env` - Environment configuration (gitignored)
- `compose.yml.old` - Renamed old compose file

**Completion Notes:**
All acceptance criteria satisfied. Docker Compose stack fully functional with:
- ✅ All 5 services running and healthy
- ✅ PostgreSQL 16.10 with uuid-ossp extension and eaf schema containing complete Axon Framework event store structure
- ✅ Keycloak 26.4.2 with eaf realm, 3 test users with proper roles and tenant_id attributes, eaf-api client configured
- ✅ Redis 7.2 responding to ping commands
- ✅ Prometheus scraping configuration validated
- ✅ Grafana 12.2.0 healthy and accessible
- ✅ All ports configurable via environment variables
- ✅ Health checks operational for all services
- ✅ Multi-architecture support (amd64, arm64) via standard Docker images

**Change Log:**
- 2025-11-02: Initial implementation - All Docker Compose infrastructure complete

---

## User Story

As a framework developer,
I want a Docker Compose stack with PostgreSQL, Keycloak, Redis, Prometheus, and Grafana,
So that I have all infrastructure services for local development.

---

## Acceptance Criteria

1. ✅ docker-compose.yml created with services: postgres (16.10), keycloak (26.4.2), redis (7.2), prometheus, grafana (12.2)
2. ✅ Custom Keycloak configuration in docker/keycloak/ with realm-export.json (test users, roles)
3. ✅ PostgreSQL init scripts in docker/postgres/init-scripts/ (schema creation, RLS setup)
4. ✅ Prometheus configuration in docker/prometheus/prometheus.yml
5. ✅ All services start successfully with docker-compose up
6. ✅ Health checks pass for all services
7. ✅ Configurable ports via environment variables (e.g., GRAFANA_PORT)

---

## Prerequisites

**Story 1.4** - Create Version Catalog

---

## Technical Notes

### Required Services

**PostgreSQL 16.10:**
- Port: 5432
- Database: eaf
- User: eaf_user
- Init scripts: Create schemas, enable extensions (uuid-ossp)

**Keycloak 26.4.2:**
- Port: 8080
- Realm: eaf
- Test users:
  - admin (role: WIDGET_ADMIN)
  - viewer (role: WIDGET_VIEWER)
- Client: eaf-api (confidential)

**Redis 7.2:**
- Port: 6379
- Use: JWT revocation cache (Epic 3)

**Prometheus:**
- Port: 9090
- Scrape targets: EAF application (port 8081)

**Grafana 12.2:**
- Port: 3000
- Datasource: Prometheus
- Note: Dashboards deferred to Post-MVP

---

## Implementation Checklist

- [x] Create docker-compose.yml with all 5 services
- [x] Create docker/postgres/init-scripts/01-init.sql (schemas, extensions)
- [x] Create docker/keycloak/realm-export.json (realm, users, roles, client)
- [x] Create docker/prometheus/prometheus.yml (scrape config)
- [x] Configure health checks for all services
- [x] Add .env.example with default ports
- [x] Test: `docker-compose up -d` - all services start
- [x] Test: Access Keycloak at http://localhost:8080
- [x] Test: Access Prometheus at http://localhost:9090
- [x] Test: Access Grafana at http://localhost:3000
- [x] Commit: "Add Docker Compose development stack with all services"

---

## Test Evidence

- [x] `docker-compose up -d` starts all 5 services
- [x] `docker-compose ps` shows all services healthy
- [x] PostgreSQL connectable: `psql -h localhost -U eaf_user -d eaf` - Version 16.10 confirmed
- [x] PostgreSQL extensions verified: uuid-ossp (1.1), pg_trgm (1.6)
- [x] PostgreSQL EAF schema created with Axon tables: domain_event_entry, snapshot_event_entry, saga_entry, token_entry, dead_letter_entry
- [x] Keycloak accessible: http://localhost:8080 (HTTP 302 redirect)
- [x] Keycloak realm 'eaf' imported successfully
- [x] Redis responding: `docker exec eaf-redis redis-cli ping` → PONG
- [x] Prometheus accessible: http://localhost:9090/-/ready (HTTP 200)
- [x] Prometheus config validated: `promtool check config` → SUCCESS
- [x] Grafana accessible: http://localhost:3000/api/health (HTTP 200)

---

## Definition of Done

- [x] All acceptance criteria met
- [x] All services start successfully
- [x] Health checks pass
- [x] Test users exist in Keycloak (admin, viewer, tenant-b-admin)
- [x] PostgreSQL schemas created (eaf schema with Axon tables)
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 1.4 - Create Version Catalog
**Next Story:** Story 1.6 - One-Command Initialization Script

---

## References

- PRD: FR001 (Development Environment Setup)
- Architecture: Section 19 (Development Environment)
- Tech Spec: Section 2.3 (Infrastructure Stack)
