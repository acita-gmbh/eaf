# Story 1.6: One-Command Initialization Script

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR001, FR025 (Local Development Workflow)

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

- [ ] Create scripts/init-dev.sh (main orchestration)
- [ ] Create scripts/health-check.sh (service validation)
- [ ] Create scripts/seed-data.sh (test data loader)
- [ ] Create scripts/install-git-hooks.sh (Git hooks installer)
- [ ] Make all scripts executable (chmod +x)
- [ ] Test on clean system: ./scripts/init-dev.sh
- [ ] Verify all services accessible
- [ ] Verify completion time <5 minutes
- [ ] Test error handling (e.g., Docker not running)
- [ ] Commit: "Add one-command development environment initialization"

---

## Test Evidence

- [ ] `./scripts/init-dev.sh` completes successfully
- [ ] All services healthy after initialization
- [ ] PostgreSQL connectable with test credentials
- [ ] Keycloak realm "eaf" exists with test users
- [ ] Redis responding to commands
- [ ] Git hooks installed in .git/hooks/
- [ ] Script output clear and informative

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Script tested on clean system
- [ ] Completion time <5 minutes
- [ ] Clear error messages on failure
- [ ] Documentation in README.md updated
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 1.5 - Docker Compose Development Stack
**Next Story:** Story 1.7 - DDD Base Classes

---

## References

- PRD: FR001, FR025
- Architecture: Section 19 (Development Environment)
- Tech Spec: Section 3 (FR001, FR025 Implementation)
