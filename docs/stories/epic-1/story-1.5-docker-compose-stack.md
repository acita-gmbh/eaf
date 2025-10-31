# Story 1.5: Docker Compose Development Stack

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR001

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

- [ ] Create docker-compose.yml with all 5 services
- [ ] Create docker/postgres/init-scripts/01-init.sql (schemas, extensions)
- [ ] Create docker/keycloak/realm-export.json (realm, users, roles, client)
- [ ] Create docker/prometheus/prometheus.yml (scrape config)
- [ ] Configure health checks for all services
- [ ] Add .env.example with default ports
- [ ] Test: `docker-compose up -d` - all services start
- [ ] Test: Access Keycloak at http://localhost:8080
- [ ] Test: Access Prometheus at http://localhost:9090
- [ ] Test: Access Grafana at http://localhost:3000
- [ ] Commit: "Add Docker Compose development stack with all services"

---

## Test Evidence

- [ ] `docker-compose up -d` starts all 5 services
- [ ] `docker-compose ps` shows all services healthy
- [ ] PostgreSQL connectable: `psql -h localhost -U eaf_user -d eaf`
- [ ] Keycloak accessible: http://localhost:8080
- [ ] Redis responding: `redis-cli -h localhost ping` → PONG
- [ ] Prometheus accessible: http://localhost:9090
- [ ] Grafana accessible: http://localhost:3000

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] All services start successfully
- [ ] Health checks pass
- [ ] Test users exist in Keycloak
- [ ] PostgreSQL schemas created
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
