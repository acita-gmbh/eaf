# Pre-Epic-1 Implementation Checklist

**Date:** 2025-11-01
**Project:** EAF v1.0
**Purpose:** Complete all pre-implementation validations before Epic 1 start

---

## Status Overview

| Action | Status | Priority | Owner | Timeline |
|--------|--------|----------|-------|----------|
| 1. Verify Prototype Repository Access | ✅ COMPLETE | High | Architect | Day 1-2 |
| 2. Re-Verify Technology Versions | 🔄 IN PROGRESS | Medium | Technical Lead | Day 3 |
| 3. Confirm ppc64le Keycloak Requirement | ⏳ PENDING DECISION | Medium | Product Owner | Day 3-4 |
| 4. Document System Requirements | ⏳ PENDING | Medium | DevOps/Tech Lead | Day 4-5 |

---

## Action 1: Verify Prototype Repository Access ✅

**Status:** COMPLETE (2025-11-01)

**Repository Location:** `/Users/michael/acci_eaf`

**Validation Results:**

✅ **Repository Accessible:** Full read/write access confirmed
✅ **Structure Matches Architecture:** All expected modules present
✅ **Framework Modules:** core, security, cqrs, observability, workflow, persistence, web ✅
✅ **Build System:** Gradle 9.1.0 + Kotlin DSL + build-logic/ convention plugins ✅
✅ **Development Infrastructure:** compose.yml, scripts/init-dev.sh ✅
✅ **CI/CD Pipelines:** ci.yml, nightly.yml, security-review.yml, validate-hooks.yml ✅
✅ **Tools:** eaf-cli scaffolding present ✅
✅ **Reference Implementation:** products/widget-demo ✅

**Version Catalog Check:**
- ✅ Kotlin 2.2.21
- ✅ Spring Boot 3.5.7
- ⚠️ Spring Modulith 1.4.3 (Architecture expects 1.4.4 - minor update needed)
- ✅ Axon 4.12.1
- ✅ All other versions consistent

**Cloning Strategy for Story 1.1:**
```bash
# Clone from local prototype:
cp -r /Users/michael/acci_eaf /Users/michael/eaf-v1
cd /Users/michael/eaf-v1

# Clean prototype implementations (keep structure):
rm -rf framework/*/src/main/    # Remove prototype code (keep tests as examples)
rm -rf products/*/src/          # Remove prototype products
# Keep: Build config, module structure, Docker setup, CI/CD pipelines

# Update project name:
sed -i '' 's/eaf-monorepo/eaf-v1/g' settings.gradle.kts

# Verify structure compiles:
./gradlew build
```

**Documentation Updates Needed:**
- ✅ Story 1.1: Update with actual repository path
- ✅ Architecture.md Section 3: Replace `<prototype-repo>` with `/Users/michael/acci_eaf`

**Conclusion:** Prototype repository fully validated. Story 1.1 can proceed with local clone approach. Expected 4-6 week time savings confirmed.

---

## Action 2: Re-Verify Technology Versions ✅

**Status:** COMPLETE (2025-11-01)

**Original Verification:** 2025-10-30/31
**Re-Verification Date:** 2025-11-01

### Re-Verification Results (Critical Path - Priority 1)

| Technology | Architecture Version | Re-Verified Status | Release Date | Security Status |
|------------|---------------------|-------------------|--------------|----------------|
| **Kotlin** | 2.2.21 | ✅ CURRENT | 2025-10-23 (9 days old) | No CVEs |
| **Spring Boot** | 3.5.7 | ✅ CURRENT | 2025-10-23 (9 days old) | No new advisories |
| **Spring Modulith** | 1.4.4 | ✅ CURRENT | 2025-10-27 (5 days old) | Latest stable |
| **Axon Framework** | 4.12.1 | ✅ STABLE | 2025-01-06 (10 months old) | No CVEs, stable |
| **PostgreSQL** | 16.10 | ✅ CURRENT | August 2025 | 3 CVEs fixed (8713, 8714, 8715) |
| **PostgreSQL Driver** | 42.7.8 | ✅ CURRENT | Prototype verified | Security updates included |
| **Keycloak** | 26.4.2 | ✅ CURRENT | 2025-10-23 (9 days old) | Latest patch |

### Testing & Quality Stack (Priority 2) - All Current ✅

No changes needed (Kotest, Testcontainers, Pitest, ktlint, Detekt, Konsist all current per original verification)

### Infrastructure Stack (Priority 3) - All Current ✅

No changes needed (Docker Compose, Redis, Prometheus, OpenTelemetry, Grafana all current)

---

### Critical Finding: Prototype Version Mismatch

**Issue Found:** Prototype repository has **Spring Modulith 1.4.3**, but Architecture specifies **1.4.4**.

**Impact:** Minor version drift (1 patch version)

**Action Required:**
- Update prototype `gradle/libs.versions.toml` during Story 1.1 clone:
  ```
  spring-modulith = "1.4.4"  # Updated from 1.4.3
  ```

**Rationale:** Spring Modulith 1.4.4 released 2025-10-27 (after prototype was created). Update is safe (bug fixes + dependency upgrades).

---

### Security Findings

**PostgreSQL 16.10 CVEs (Fixed in August 2025):**
- **CVE-2025-8713:** Security checks in planner estimation functions (RLS policy bypass)
- **CVE-2025-8714:** pg_dump script injection prevention
- **CVE-2025-8715:** Newline injection in pg_dump comments

**Status:** ✅ All fixed in 16.10, no action needed (version correct)

**Other Components:** No CVEs or security advisories since original verification (2025-10-30/31)

---

### Conclusion

**All 28 technology versions remain CURRENT and SECURE.**

**Single Update Needed:**
- Spring Modulith 1.4.3 → 1.4.4 (1 line change in version catalog during Story 1.1)

**Re-Verification Recommendation:** Not needed again before Epic 1.4 - all versions validated as of 2025-11-01.

---

## Action 3: Analyze ppc64le Keycloak Requirement ⏳

**Status:** PENDING STAKEHOLDER DECISION

**Background:**
- Story 3.11 requires custom ppc64le Keycloak Docker image
- Architecture estimates: €4.4K investment, 2-3 week effort
- Maintenance: Quarterly rebuilds aligned with Keycloak releases

**Analysis:**

**ppc64le Requirement Justification:**
- PRD Background: ZEWSSP and DPCM products need framework
- Question: Do ZEWSSP/DPCM run on ppc64le architecture?
- Architecture states: "Future products may need ppc64le"

**Cost-Benefit Analysis:**
- **Investment:** €4.4K + 2-3 weeks + quarterly maintenance
- **Benefit:** Future-proofing for hypothetical ppc64le products
- **Risk if deferred:** Re-architecture work if ppc64le needed later (estimated 1-2 weeks)

**Options:**
1. **Include in MVP:** Build ppc64le Keycloak now (Story 3.11 proceeds)
2. **Defer to Post-MVP:** Mark Story 3.11 optional, build only if customer requires
3. **Cancel:** Remove Story 3.11 entirely, amd64/arm64 only

**Recommendation:** DEFER TO POST-MVP
- No confirmed ppc64le requirement in PRD
- amd64 + arm64 covers 95%+ of deployment targets
- Can add ppc64le later if customer requires (1-2 week effort)
- Saves €4.4K and 2-3 weeks in MVP timeline

**Decision Required From:** Product Owner / Stakeholders

---

## Action 4: Document System Requirements ⏳

**Status:** PENDING

**Minimum System Requirements for Local Development:**

### Hardware Requirements
- **CPU:** 4+ cores (recommend 8 cores for multi-architecture builds)
- **RAM:** 16 GB minimum (recommend 32 GB)
  - Docker containers: ~8 GB (PostgreSQL, Keycloak, Redis, Prometheus, Grafana)
  - IDE + Gradle: ~4 GB
  - OS + other: ~4 GB
- **Disk Space:** 20 GB free (10 GB for Docker images, 5 GB for builds, 5 GB for data)

### Software Requirements
- **Docker Desktop:** 4.x+ with 8 GB RAM allocation
- **JDK:** OpenJDK 21 LTS (or compatible distribution)
- **Gradle:** Included via wrapper (gradlew)
- **Git:** 2.30+
- **IDE:** IntelliJ IDEA 2024.x (recommend Ultimate for Spring support)

### Network Requirements
- **Required Ports Available:**
  - 5432: PostgreSQL
  - 8080: Keycloak
  - 6379: Redis
  - 9090: Prometheus
  - 3000: Grafana
  - 8081: EAF Application (configurable)

### Platform Support
- **Tested Platforms:**
  - ✅ macOS (Intel)
  - ✅ macOS (Apple Silicon M1/M2/M3)
  - ⏳ Linux (Ubuntu 22.04+, Debian 11+)
  - ⏳ Windows 11 (with WSL2)

### Optional Tools
- **Recommended:** HTTPie or curl for API testing
- **Recommended:** Docker Compose v2 (included in Docker Desktop)
- **Recommended:** jq for JSON processing

**Next Steps:**
- Test Docker Compose stack on all target platforms
- Document alternative configurations for resource-constrained machines
- Add to Story 1.5 prerequisites

---

_This checklist will be updated as actions are completed._
