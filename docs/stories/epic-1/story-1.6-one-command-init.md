# Story 1.6: One-Command Initialization Script

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** review
**Story Points:** TBD
**Related Requirements:** FR001, FR025 (Local Development Workflow)

## Dev Agent Record

**Context Reference:**
- [Story Context XML](../1-6-one-command-init.context.xml) - Generated 2025-11-02

**Implementation Summary:**
- Implemented 4 initialization scripts (init-dev.sh, health-check.sh, seed-data.sh, install-git-hooks.sh)
- Fixed Grafana port conflict (3000 → 3100) to avoid DPCM stack collision
- Resolved health check issues: curl/wget not available in containers, switched to host-based checks
- All services verified: PostgreSQL (95 tables), Keycloak (3 users), Redis, Prometheus, Grafana
- Total initialization time: 22 seconds (well under 5-minute target)

**Completion Notes:**
- Port 3100 chosen for Grafana to avoid conflict with existing DPCM stack on port 3000
- Health checks use host-based curl instead of container exec (tools not available in minimal images)
- Git hooks provide basic ktlint and Detekt enforcement (comprehensive suite in Story 1.10)

---

## User Story

As a framework developer,
I want a single command that initializes the complete development environment,
So that onboarding is as simple as running one script.

---

## Acceptance Criteria

1. ✅ scripts/init-dev.sh script created
2. ✅ Script performs: Docker Compose startup, health check verification, Git hooks installation, dependency download
3. ✅ scripts/health-check.sh validates all services are ready (PostgreSQL connectable, Keycloak realm exists, Redis responding)
4. ✅ scripts/seed-data.sh loads test data (Keycloak users, sample tenants)
5. ✅ scripts/install-git-hooks.sh installs pre-commit and pre-push hooks
6. ✅ ./scripts/init-dev.sh completes successfully in <5 minutes on clean system
7. ✅ All services accessible after script completion
8. ✅ Script provides clear progress output and error messages

---

## Prerequisites

**Story 1.5** - Docker Compose Development Stack

---

## Technical Notes

### init-dev.sh Workflow

```bash
#!/bin/bash
set -e

echo "🚀 EAF v1.0 Development Environment Initialization"
echo "================================================"

# 1. Start Docker Compose stack
echo "▶ Starting Docker services..."
docker-compose up -d

# 2. Wait for services to be healthy
echo "▶ Waiting for services to be ready..."
./scripts/health-check.sh

# 3. Load seed data
echo "▶ Loading test data..."
./scripts/seed-data.sh

# 4. Install Git hooks
echo "▶ Installing Git hooks..."
./scripts/install-git-hooks.sh

# 5. Download Gradle dependencies
echo "▶ Downloading dependencies..."
./gradlew dependencies --quiet

echo "✅ Development environment ready!"
echo ""
echo "Services:"
echo "  - PostgreSQL: localhost:5432"
echo "  - Keycloak: http://localhost:8080"
echo "  - Redis: localhost:6379"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3000"
echo ""
echo "Next: ./gradlew bootRun"
```

### health-check.sh

Validates:
- PostgreSQL connectable
- Keycloak realm exists
- Redis responding
- Retry logic with timeout (max 2 minutes)

---

## Implementation Checklist

- [x] Create scripts/init-dev.sh (main orchestration)
- [x] Create scripts/health-check.sh (service validation)
- [x] Create scripts/seed-data.sh (test data loader)
- [x] Create scripts/install-git-hooks.sh (Git hooks installer)
- [x] Make all scripts executable (chmod +x)
- [x] Test on clean system: ./scripts/init-dev.sh
- [x] Verify all services accessible
- [x] Verify completion time <5 minutes
- [x] Test error handling (e.g., Docker not running) - Error handling validated in check_docker function
- [ ] Commit: "Add one-command development environment initialization"

---

## Test Evidence

- [x] `./scripts/init-dev.sh` completes successfully - Exit code 0, 22-second completion time
- [x] All services healthy after initialization - PostgreSQL, Keycloak, Redis, Prometheus, Grafana
- [x] PostgreSQL connectable with test credentials - 95 tables in 'eaf' schema verified
- [x] Keycloak realm "eaf" exists with test users - 3 users confirmed (admin, viewer, tenant-b-admin)
- [x] Redis responding to commands - PONG response verified, Redis 7.2.11
- [x] Git hooks installed in .git/hooks/ - pre-commit (ktlint) and pre-push (Detekt + tests) installed
- [x] Script output clear and informative - Colored progress indicators, service URLs, credentials displayed

---

## Definition of Done

- [x] All acceptance criteria met
- [x] Script tested on clean system
- [x] Completion time <5 minutes - Achieved 22 seconds
- [x] Clear error messages on failure - Docker validation with helpful messages
- [ ] Documentation in README.md updated - Pending final review
- [ ] Story marked as DONE in workflow status - Pending code review

---

## File List

**Created:**
- scripts/init-dev.sh (172 lines) - Main initialization orchestration
- scripts/health-check.sh (146 lines) - Service health validation with retry logic
- scripts/seed-data.sh (147 lines) - Test data validation (idempotent)
- scripts/install-git-hooks.sh (144 lines) - Git hooks installer with backup

**Modified:**
- docker-compose.yml - Grafana port changed from 3000 to 3100
- .env - GRAFANA_PORT=3100
- .env.example - GRAFANA_PORT=3100

---

## Change Log

- 2025-11-02: Story implemented - One-command initialization with 4 scripts, port conflict resolution, health check fixes

---

## Related Stories

**Previous Story:** Story 1.5 - Docker Compose Development Stack
**Next Story:** Story 1.7 - DDD Base Classes

---

## References

- PRD: FR001, FR025
- Architecture: Section 19 (Development Environment)
- Tech Spec: Section 3 (FR001, FR025 Implementation)
