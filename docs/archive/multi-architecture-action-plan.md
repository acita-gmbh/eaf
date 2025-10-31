# Multi-Architecture Support: Action Plan
**Date:** 2025-10-30
**Owner:** Michael Walloschke (Staff Engineer)
**Status:** Awaiting Decision

---

## Immediate Actions Required (This Week)

### Action 1: Confirm Customer ppc64le Requirement 🔴 CRITICAL
**Owner:** Product Manager / Sales
**Deadline:** 2025-11-01 (2 days)

**Questions to Ask Customer:**
1. Is ppc64le (IBM POWER9+) deployment **mandatory** or **nice-to-have**?
2. Would customer accept **hybrid deployment** (PostgreSQL/Redis on ppc64le, Keycloak/EAF API on amd64)?
3. Would customer accept **amd64-only** deployment if ppc64le not feasible?
4. Does customer have existing amd64 infrastructure available?
5. Can customer provide **ppc64le test environment** for validation?
6. What is customer's timeline for ppc64le requirement? (Immediate / 3 months / 6 months)

**Output:** Email or meeting notes documenting customer responses

**Decision Impact:**
- **Mandatory:** Proceed to Action 2 (business value assessment)
- **Negotiable:** Recommend deferring to Post-MVP, negotiate alternatives
- **Nice-to-have:** Recommend NOT implementing for MVP

---

### Action 2: Assess Business Value 💰
**Owner:** Product Manager
**Deadline:** 2025-11-03 (4 days)

**Calculations:**
1. **Contract Value:**
   - Estimated annual contract value: €___K
   - Multi-year contract length: ___ years
   - Total contract value: €___K

2. **Win Probability:**
   - Probability of winning WITH ppc64le support: ___%
   - Probability of winning WITHOUT ppc64le support: ___%
   - Delta (impact of ppc64le): ___% improvement

3. **ROI Calculation:**
   - Expected value: Contract Value × Win Probability = €___K
   - Implementation cost: €14,100
   - Ongoing cost (3 years): €27,600
   - Total cost: €41,700
   - ROI: (Expected Value - Total Cost) / Total Cost = ___x

**Decision Criteria:**
- **ROI > 3x:** Strong business case, recommend proceeding
- **ROI 1-3x:** Marginal case, negotiate with customer
- **ROI < 1x:** Negative return, recommend NOT implementing

**Output:** Business case document with ROI analysis

---

### Action 3: Make GO/NO-GO Decision 🎯
**Owner:** Product Manager + Tech Lead (Michael Walloschke)
**Deadline:** 2025-11-04 (5 days)

**Decision Framework:**

```
IF (Customer Requirement == MANDATORY)
   AND (Contract Value > €100K/year)
   AND (Win Probability Delta > 30%)
   AND (Team Bandwidth Available Post-MVP)
THEN
   Decision = GO (proceed with Phase 2 ppc64le support post-Epic-9)

ELSE IF (Customer Requirement == NEGOTIABLE)
   AND (Customer Accepts Hybrid Deployment)
THEN
   Decision = DEFER (implement hybrid deployment alternative)

ELSE
   Decision = NO-GO (focus on amd64 + arm64 only)
```

**Document Decision:**
- Use template in `/docs/multi-architecture-executive-summary.md`
- Sign off by Product Manager and Tech Lead
- Communicate to team and customer

---

## Phase 1: MVP (Weeks 1-13) - NO ACTION NEEDED ✅

**Current Status:** On track for amd64 + arm64 deployment

**What's Already Working:**
- All EAF dependencies have official amd64/arm64 Docker images
- CI/CD pipeline builds multi-arch images automatically
- Developer environments work on Intel/Apple Silicon Macs
- Testcontainers integration tests pass on both architectures

**Verification (Before Epic 9 Completion):**
- [ ] Test EAF deployment on amd64 Linux (developer workstation or CI)
- [ ] Test EAF deployment on arm64 Linux (GitHub ARM runner or AWS Graviton)
- [ ] Verify docker-compose.yml pulls correct architecture automatically
- [ ] Confirm Majlinda can develop on amd64 or arm64 Mac successfully

**No additional work required for MVP completion.**

---

## Phase 2: ppc64le Investigation (Weeks 14-15) - IF GO DECISION

### Week 14: Component Verification Sprint

**Day 1: Infrastructure Setup**
- [ ] Provision IBM Cloud POWER Virtual Server instance
  - Configuration: 8 vCPU (POWER9), 16GB RAM, RHEL 9 or Ubuntu 22.04
  - Cost: ~€0.25/hour × 40 hours = €10
  - Link: https://cloud.ibm.com/catalog/services/power-systems-virtual-server
- [ ] Install Docker Engine on ppc64le instance
  ```bash
  # RHEL 9
  sudo dnf install docker-ce docker-ce-cli containerd.io
  sudo systemctl start docker

  # Ubuntu 22.04
  sudo apt-get update
  sudo apt-get install docker-ce docker-ce-cli containerd.io
  sudo systemctl start docker
  ```
- [ ] Verify architecture: `uname -m` (should output `ppc64le`)

**Day 2-3: Official Components Testing**
- [ ] Test PostgreSQL 16 official image
  ```bash
  docker pull postgres:16-bookworm
  docker run --rm -e POSTGRES_PASSWORD=test -p 5432:5432 postgres:16-bookworm
  # Connect with psql and verify functionality
  ```
- [ ] Test Redis 8.2 official image
  ```bash
  docker pull redis:8.2.2-bookworm
  docker run --rm -p 6379:6379 redis:8.2.2-bookworm
  # Connect with redis-cli and verify functionality
  ```
- [ ] Test Eclipse Temurin OpenJDK 21 image
  ```bash
  docker pull eclipse-temurin:21-jdk-jammy
  docker run --rm eclipse-temurin:21-jdk-jammy java -version
  # Verify JVM info and architecture
  ```
- [ ] **Document findings:** Architecture verified, performance baseline, any issues

**Day 4: Custom Keycloak Build**
- [ ] Clone EAF repository on ppc64le instance
- [ ] Create Dockerfile for Keycloak ppc64le (see Appendix A)
- [ ] Build Keycloak image
  ```bash
  docker build -t eaf/keycloak:26.4.0-ppc64le -f Dockerfile.keycloak .
  # Expect: 10-15 minutes build time
  ```
- [ ] Test Keycloak startup
  ```bash
  docker run --rm -p 8080:8080 \
    -e KEYCLOAK_ADMIN=admin \
    -e KEYCLOAK_ADMIN_PASSWORD=admin \
    eaf/keycloak:26.4.0-ppc64le
  ```
- [ ] Verify OIDC endpoints
  ```bash
  curl http://localhost:8080/realms/master/.well-known/openid-configuration
  ```
- [ ] **Document findings:** Build success, startup time, OIDC flow verification

**Day 5: Flowable BPMN Verification**
- [ ] Create test Spring Boot application with Flowable dependency
  ```kotlin
  // build.gradle.kts
  dependencies {
      implementation("org.flowable:flowable-spring-boot-starter:7.1.0")
      implementation("org.postgresql:postgresql:42.7.1")
  }
  ```
- [ ] Build and run on ppc64le
  ```bash
  ./gradlew bootJar
  java -jar build/libs/flowable-test.jar
  ```
- [ ] Deploy sample BPMN process and verify execution
- [ ] **Document findings:** Compatibility confirmed, performance notes, any issues

**Deliverables (End of Week 14):**
- Component verification report (1-2 pages)
- ppc64le Keycloak Docker image (if successful)
- Risk assessment update
- **GO/NO-GO recommendation** for Week 15 decision

---

### Week 15: Feasibility Assessment & Decision

**Day 1-2: Performance Baseline**
- [ ] Deploy full EAF stack (PostgreSQL, Redis, Keycloak, EAF API) on ppc64le
- [ ] Run integration test suite and measure execution time
  - Compare to amd64 baseline: ____ minutes (amd64) vs ____ minutes (ppc64le)
- [ ] Run load test with Apache JMeter
  - API latency p95: ____ ms (amd64) vs ____ ms (ppc64le)
  - Event processing lag: ____ ms (amd64) vs ____ ms (ppc64le)
- [ ] **Document findings:** Performance comparison, bottlenecks, tuning recommendations

**Day 3: Cost Analysis Refinement**
- [ ] Calculate actual build times on ppc64le hardware
  - Keycloak: ____ minutes (actual) vs 10-15 min (estimated)
  - EAF API: ____ minutes (actual) vs 5-8 min (estimated)
- [ ] Estimate ongoing maintenance effort
  - Security patches: ____ hours/quarter
  - Dependency updates: ____ hours/quarter
- [ ] Update ROI calculation with actual data

**Day 4: Risk Assessment**
- [ ] Identify blocking issues (if any)
- [ ] Document mitigation strategies
- [ ] Estimate timeline buffer for unknowns

**Day 5: Final Decision Meeting**
- [ ] Present findings to Product Manager and Tech Lead
- [ ] Review business case with updated data
- [ ] **MAKE GO/NO-GO DECISION** for Phase 3 implementation
- [ ] Document decision and rationale
- [ ] Communicate to team and customer

**Deliverables (End of Week 15):**
- Feasibility assessment report (3-5 pages)
- Performance benchmark results
- Updated cost/ROI analysis
- **Signed GO/NO-GO decision document**

---

## Phase 3: ppc64le Implementation (Weeks 16-19) - IF GO DECISION

### Week 16: Build Infrastructure

**Day 1-2: CI/CD Pipeline Configuration**
- [ ] Set up Docker Buildx multi-arch builder in GitHub Actions
  ```yaml
  # .github/workflows/multi-arch-build.yml
  - name: Set up QEMU
    uses: docker/setup-qemu-action@v3

  - name: Set up Docker Buildx
    uses: docker/setup-buildx-action@v3

  - name: Build multi-arch images
    uses: docker/build-push-action@v5
    with:
      platforms: linux/amd64,linux/arm64,linux/ppc64le
      tags: ghcr.io/axians/eaf/api:${{ github.sha }}
      push: true
  ```
- [ ] Configure architecture-specific cache keys
- [ ] Test pipeline with sample image (hello-world)

**Day 3: Image Registry Setup**
- [ ] Create GitHub Container Registry repositories
  - `ghcr.io/axians/eaf/api`
  - `ghcr.io/axians/eaf/keycloak`
  - `ghcr.io/axians/eaf/grafana` (optional)
- [ ] Configure registry authentication in CI/CD
- [ ] Set up image retention policies (keep last 10 tags)

**Day 4-5: Test Multi-Arch Pipeline**
- [ ] Trigger build pipeline for EAF API
- [ ] Verify images pushed for all architectures (amd64, arm64, ppc64le)
- [ ] Inspect manifest list
  ```bash
  docker manifest inspect ghcr.io/axians/eaf/api:latest
  ```
- [ ] Test pulling on each architecture
  ```bash
  # On amd64 host
  docker pull ghcr.io/axians/eaf/api:latest
  docker inspect ghcr.io/axians/eaf/api:latest | jq '.[0].Architecture'
  # Expected: "amd64"

  # On ppc64le host
  docker pull ghcr.io/axians/eaf/api:latest
  docker inspect ghcr.io/axians/eaf/api:latest | jq '.[0].Architecture'
  # Expected: "ppc64le"
  ```

**Deliverables (End of Week 16):**
- Multi-arch CI/CD pipeline functional
- Images published to GitHub Container Registry
- Pipeline documentation (runbook)

---

### Week 17: Custom Component Builds

**Day 1-2: Keycloak ppc64le Image**
- [ ] Create production-ready Dockerfile (see Appendix A)
- [ ] Integrate Keycloak build into CI/CD pipeline
- [ ] Build multi-arch Keycloak image (amd64, arm64, ppc64le)
- [ ] Test on all architectures
- [ ] Document security hardening steps

**Day 3: Grafana ppc64le Image (Optional)**
- [ ] Evaluate: Use community image vs build from source
- [ ] If building: Create Dockerfile, integrate into CI/CD
- [ ] If skipping: Document decision to deploy Grafana on amd64/arm64 only
- [ ] Test on ppc64le (if building)

**Day 4-5: Docker Compose Updates**
- [ ] Update `docker-compose.yml` with multi-arch image tags
  ```yaml
  services:
    postgres:
      image: postgres:16-bookworm  # Multi-arch manifest

    redis:
      image: redis:8.2.2-bookworm  # Multi-arch manifest

    keycloak:
      image: ghcr.io/axians/eaf/keycloak:26.4.0  # Custom multi-arch

    eaf-api:
      image: ghcr.io/axians/eaf/api:${VERSION}  # Custom multi-arch
  ```
- [ ] Test deployment on amd64: `docker-compose up`
- [ ] Test deployment on arm64: `docker-compose up`
- [ ] Test deployment on ppc64le: `docker-compose up`
- [ ] Verify architecture-specific images pulled automatically

**Deliverables (End of Week 17):**
- Custom Keycloak multi-arch image in registry
- (Optional) Custom Grafana multi-arch image in registry
- Updated docker-compose.yml with multi-arch support
- Deployment guide updated

---

### Week 18: Integration Testing

**Day 1: Test Environment Setup**
- [ ] Deploy EAF full stack on ppc64le IBM Cloud instance
- [ ] Configure environment variables (DB passwords, Keycloak secrets)
- [ ] Verify all containers start successfully
- [ ] Check logs for warnings or errors

**Day 2-3: Integration Test Suite Execution**
- [ ] Run full integration test suite on ppc64le
  ```bash
  ./gradlew integrationTest -Dspring.profiles.active=ppc64le-test
  ```
- [ ] Compare test execution times
  - amd64: ____ minutes
  - ppc64le: ____ minutes
  - Slowdown factor: ____x
- [ ] Investigate and fix any test failures (expect 0-2% failure rate)
- [ ] Document ppc64le-specific issues and resolutions

**Day 4: End-to-End Testing**
- [ ] Test critical user workflows on ppc64le
  - User authentication (Keycloak OIDC)
  - Widget creation (CQRS command)
  - Widget query (projection read)
  - Workflow execution (Flowable BPMN)
- [ ] Validate multi-tenancy isolation
  - Create tenants A and B
  - Verify tenant A cannot access tenant B data
  - Check PostgreSQL Row-Level Security policies
- [ ] Test observability
  - Verify Prometheus metrics endpoint
  - Check structured JSON logs
  - Confirm trace IDs propagate

**Day 5: Performance Validation**
- [ ] Run load test with Apache JMeter (100 concurrent users)
  - API latency p95: Target <200ms
  - Event processing lag: Target <10s
  - Database query latency: Target <50ms
- [ ] Compare to amd64 baseline
- [ ] Document performance characteristics
- [ ] Tune JVM settings if needed (GC, heap size)

**Deliverables (End of Week 18):**
- Integration test report (pass/fail, issues found)
- E2E test validation (user workflows verified)
- Performance benchmark report (vs amd64 baseline)
- Tuning recommendations (if applicable)

---

### Week 19: Documentation & Handoff

**Day 1-2: Deployment Documentation**
- [ ] Write ppc64le deployment guide
  - Prerequisites (RHEL 9 / Ubuntu 22.04, Docker Engine)
  - Image registry authentication
  - docker-compose.yml configuration
  - Environment variable setup
  - Startup and health check verification
  - Troubleshooting common issues
- [ ] Create architecture decision record (ADR)
  - Why ppc64le support added
  - Custom build decisions (Keycloak, Grafana)
  - Performance tuning recommendations
  - Maintenance responsibilities

**Day 3: Build & Maintenance Runbooks**
- [ ] Document custom Keycloak build procedure
  - Dockerfile explanation
  - Build command
  - Testing procedure
  - Security hardening checklist
- [ ] Document CI/CD pipeline
  - GitHub Actions workflow structure
  - Multi-arch build process
  - Cache optimization strategy
  - Troubleshooting build failures
- [ ] Document maintenance procedures
  - Security patch process
  - Dependency update process
  - Testing checklist
  - Rollback procedure

**Day 4: Customer Validation Preparation**
- [ ] Create customer test plan
  - Deployment steps
  - Smoke test checklist
  - Performance acceptance criteria
  - Known limitations and workarounds
- [ ] Package deployment artifacts
  - docker-compose.yml
  - Configuration templates (.env.example)
  - Deployment guide
  - Troubleshooting guide
- [ ] Schedule customer validation session

**Day 5: Team Handoff**
- [ ] Conduct knowledge transfer session with team
  - Architecture overview
  - Build and deployment procedures
  - Maintenance responsibilities
  - Escalation contacts
- [ ] Update project documentation
  - Add ppc64le to Product Brief technical requirements
  - Update README.md with architecture support matrix
  - Link to detailed deployment guides
- [ ] Assign ongoing maintenance owner

**Deliverables (End of Week 19):**
- Comprehensive ppc64le deployment guide
- Build and maintenance runbooks
- Customer test plan and artifacts
- Team knowledge transfer complete
- **ppc64le support production-ready**

---

## Phase 4: Ongoing Maintenance (Quarterly)

### Maintenance Checklist (Run Every Quarter)

**Week 1 of Quarter: Security Patch Review**
- [ ] Subscribe to security mailing lists
  - Keycloak: keycloak-user@lists.jboss.org
  - Grafana: security@grafana.com
  - PostgreSQL: pgsql-announce@lists.postgresql.org
- [ ] Check CVE databases for new vulnerabilities
  - https://cve.mitre.org/
  - https://nvd.nist.gov/
- [ ] Review upstream release notes for security fixes

**Week 2 of Quarter: Image Rebuilds**
- [ ] Rebuild custom Keycloak image with latest version
  ```bash
  # Update Keycloak version in Dockerfile
  ARG KEYCLOAK_VERSION=26.5.0  # Updated version

  # Trigger CI/CD pipeline
  git commit -m "security: update Keycloak to 26.5.0"
  git push
  ```
- [ ] Rebuild custom Grafana image (if applicable)
- [ ] Verify images build successfully on all architectures

**Week 3 of Quarter: Testing & Validation**
- [ ] Deploy updated images to ppc64le test environment
- [ ] Run integration test suite
  ```bash
  ./gradlew integrationTest -Dspring.profiles.active=ppc64le-test
  ```
- [ ] Run smoke tests on ppc64le hardware
- [ ] Verify performance regression testing
  - Compare API latency to previous quarter baseline
  - Document any degradation (>10% considered significant)

**Week 4 of Quarter: Documentation & Reporting**
- [ ] Update version compatibility matrix
- [ ] Document any new issues discovered
- [ ] Update troubleshooting guide with new solutions
- [ ] Report to Product Manager
  - Maintenance activities completed
  - Issues encountered and resolved
  - Upcoming changes (dependency updates, etc.)

**Estimated Effort:** 20 hours/quarter (~5 hours/week for 4 weeks)

---

## Appendix A: Keycloak ppc64le Dockerfile

```dockerfile
# Keycloak ppc64le Docker Image (JVM Mode)
# Version: 26.4.0
# Architecture: linux/ppc64le

# syntax=docker/dockerfile:1.4
FROM eclipse-temurin:21-jdk-jammy AS builder

ARG KEYCLOAK_VERSION=26.4.0
WORKDIR /build

# Download Keycloak distribution
RUN apt-get update && apt-get install -y wget && \
    wget -q https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.tar.gz && \
    tar -xzf keycloak-${KEYCLOAK_VERSION}.tar.gz && \
    rm keycloak-${KEYCLOAK_VERSION}.tar.gz

# Build Keycloak (optimize for production)
WORKDIR /build/keycloak-${KEYCLOAK_VERSION}
RUN bin/kc.sh build \
    --db=postgres \
    --features=token-exchange,admin-fine-grained-authz \
    --health-enabled=true \
    --metrics-enabled=true

# --- Runtime Stage ---
FROM eclipse-temurin:21-jre-jammy

ARG KEYCLOAK_VERSION=26.4.0
ENV KC_HOME=/opt/keycloak
ENV LANG=en_US.UTF-8
ENV LC_ALL=en_US.UTF-8

WORKDIR ${KC_HOME}

# Copy built Keycloak from builder
COPY --from=builder /build/keycloak-${KEYCLOAK_VERSION} ${KC_HOME}

# Create keycloak user (security best practice)
RUN groupadd -r keycloak --gid=1000 && \
    useradd -r -g keycloak --uid=1000 -m -d ${KC_HOME} keycloak && \
    chown -R keycloak:keycloak ${KC_HOME}

# Configure Keycloak for production
ENV KC_DB=postgres \
    KC_HTTP_ENABLED=true \
    KC_HOSTNAME_STRICT=false \
    KC_PROXY=edge \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_LOG_LEVEL=INFO

# Expose ports
EXPOSE 8080 8443

# Switch to non-root user
USER keycloak

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ${KC_HOME}/bin/kc.sh show-config --all || exit 1

# Entrypoint
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
```

**Build Command:**
```bash
# Build for all architectures
docker buildx build \
  --platform linux/amd64,linux/arm64,linux/ppc64le \
  --build-arg KEYCLOAK_VERSION=26.4.0 \
  -t ghcr.io/axians/eaf/keycloak:26.4.0 \
  -f Dockerfile.keycloak \
  --push \
  .

# Build for ppc64le only (faster)
docker buildx build \
  --platform linux/ppc64le \
  --build-arg KEYCLOAK_VERSION=26.4.0 \
  -t ghcr.io/axians/eaf/keycloak:26.4.0-ppc64le \
  -f Dockerfile.keycloak \
  --load \
  .
```

**Test Command:**
```bash
# Test Keycloak startup
docker run --rm -it \
  -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -e KC_DB=dev-mem \
  ghcr.io/axians/eaf/keycloak:26.4.0

# Access admin console: http://localhost:8080/admin
# Login with admin/admin
```

---

## Appendix B: Quick Reference Links

**Documentation:**
- Multi-Architecture Analysis (Full): `/docs/multi-architecture-analysis-2025-10-30.md`
- Executive Summary: `/docs/multi-architecture-executive-summary.md`
- Product Brief: `/docs/product-brief-EAF-2025-10-30.md`

**External Resources:**
- Docker Buildx Multi-Platform: https://docs.docker.com/build/building/multi-platform/
- Eclipse Temurin Downloads: https://adoptium.net/temurin/releases/
- IBM Cloud POWER Instances: https://cloud.ibm.com/catalog/services/power-systems-virtual-server
- Keycloak Documentation: https://www.keycloak.org/documentation
- PostgreSQL Docker Hub: https://hub.docker.com/_/postgres

**Support Contacts:**
- IBM Cloud Support: https://cloud.ibm.com/docs/power-iaas
- Eclipse Temurin: adoptium-dev@eclipse.org
- Keycloak User List: keycloak-user@lists.jboss.org
- Docker Community: https://www.docker.com/community/

---

**Document Owner:** Michael Walloschke (Staff Engineer)
**Last Updated:** 2025-10-30
**Next Review:** After GO/NO-GO decision (Week 15)
**Status:** Awaiting customer requirement confirmation

