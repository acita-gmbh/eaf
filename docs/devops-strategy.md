# DVMM DevOps Strategy

**Author:** Wall-E
**Date:** 2025-11-25
**Version:** 1.0
**Framework:** Enterprise Application Framework (EAF)

---

## Executive Summary

This document defines the DevOps strategy for DVMM, covering CI/CD pipeline design, infrastructure architecture, monitoring, and disaster recovery. The strategy aligns with Enterprise Method requirements and supports the quality-first development approach.

**Key Decisions:**
- **CI/CD:** GitHub Actions with quality gates (coverage, mutation testing, architecture tests)
- **Infrastructure:** Container-based deployment on Kubernetes (future: managed K8s)
- **Monitoring:** Grafana + Prometheus + Loki stack
- **Disaster Recovery:** RTO < 4 hours, RPO < 1 hour

---

## 1. CI/CD Pipeline Architecture

### 1.1 Pipeline Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CI/CD PIPELINE                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐  │
│  │  Build  │──▶│  Test   │──▶│ Quality │──▶│ Security│──▶│ Package │  │
│  │         │   │         │   │  Gates  │   │  Scan   │   │         │  │
│  └─────────┘   └─────────┘   └─────────┘   └─────────┘   └─────────┘  │
│       │             │             │             │             │        │
│       ▼             ▼             ▼             ▼             ▼        │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                    QUALITY GATE CHECK                           │  │
│  │  Coverage ≥70% │ Mutation ≥70% │ Arch Tests │ Zero Critical    │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                    │                                   │
│                         ┌─────────┴─────────┐                         │
│                         ▼                   ▼                         │
│                    ┌─────────┐         ┌─────────┐                   │
│                    │  PASS   │         │  FAIL   │                   │
│                    │ Deploy  │         │  Block  │                   │
│                    └─────────┘         └─────────┘                   │
│                         │                                             │
│              ┌──────────┼──────────┐                                 │
│              ▼          ▼          ▼                                 │
│         ┌────────┐ ┌────────┐ ┌────────┐                            │
│         │  Dev   │ │Staging │ │  Prod  │                            │
│         └────────┘ └────────┘ └────────┘                            │
│                                                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Pipeline Stages

| Stage | Actions | Duration Target | Failure Action |
|-------|---------|-----------------|----------------|
| **Build** | Gradle build, compile | < 2 min | Block |
| **Unit Tests** | JUnit 6 tests | < 5 min | Block |
| **Integration Tests** | Testcontainers | < 10 min | Block |
| **Quality Gates** | Kover, Pitest, Konsist | < 5 min | Block |
| **Security Scan** | OWASP, Trivy | < 3 min | Block on Critical |
| **Package** | Docker build, push | < 3 min | Block |
| **Deploy Dev** | Auto-deploy | < 2 min | Alert |
| **Deploy Staging** | Manual trigger | < 5 min | Rollback |
| **Deploy Prod** | Manual approval | < 5 min | Rollback |

**Total Pipeline Time:** < 30 minutes (NFR-MAINT-11 target: 15 min for tests)

### 1.3 Quality Gates (Story 1.11)

```yaml
# .github/workflows/ci.yml
quality_gates:
  coverage:
    threshold: 70%  # Aligned with mutation score threshold
    tool: kover
    fail_on_decrease: true

  mutation_testing:
    threshold: 70%
    tool: pitest
    scope: domain,application

  architecture_tests:
    tool: konsist
    rules:
      - "eaf modules have no dvmm dependencies"
      - "domain has no infrastructure dependencies"
      - "controllers only in api module"

  security:
    critical_vulnerabilities: 0
    high_vulnerabilities: 0
    tool: owasp-dependency-check

  container:
    tool: trivy
    severity: CRITICAL,HIGH
    exit_code: 1
```

### 1.4 Branch Strategy

```
main (protected)
  │
  ├── feature/DVMM-123-feature-name
  │     └── PR → main (requires: CI pass, 1 approval)
  │
  ├── hotfix/DVMM-456-critical-fix
  │     └── PR → main (fast-track, post-deploy to prod)
  │
  └── release/v1.0.0
        └── Tag → Deploy to production
```

**Branch Protection Rules:**
- `main`: Require PR, require CI pass, require 1 approval
- No direct pushes to `main`
- Auto-delete merged branches

---

## 2. Infrastructure Architecture

### 2.1 Environment Overview

| Environment | Purpose | Deployment | Data |
|-------------|---------|------------|------|
| **Local** | Development | Docker Compose | Testcontainers |
| **Dev** | Integration testing | Auto on merge | Synthetic |
| **Staging** | Pre-production | Manual trigger | Anonymized prod |
| **Production** | Live system | Manual approval | Real |

### 2.2 Container Architecture

```yaml
# docker-compose.yml (simplified)
services:
  dvmm-api:
    image: dvmm/api:${VERSION}
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - DB_HOST=postgres
      - KEYCLOAK_URL=https://auth.example.com
    depends_on:
      - postgres
      - keycloak

  dvmm-frontend:
    image: dvmm/frontend:${VERSION}
    ports:
      - "3000:80"

  postgres:
    image: postgres:16
    volumes:
      - pgdata:/var/lib/postgresql/data
    environment:
      - POSTGRES_DB=dvmm
      - POSTGRES_USER=dvmm

  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    environment:
      - KC_DB=postgres
```

### 2.3 Kubernetes Architecture (Growth)

```yaml
# Namespace per environment
apiVersion: v1
kind: Namespace
metadata:
  name: dvmm-production

---
# Deployment with health checks
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dvmm-api
spec:
  replicas: 3
  selector:
    matchLabels:
      app: dvmm-api
  template:
    spec:
      containers:
      - name: dvmm-api
        image: dvmm/api:${VERSION}
        ports:
        - containerPort: 8080
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
```

### 2.4 Database Infrastructure

| Component | Technology | Configuration |
|-----------|------------|---------------|
| Primary DB | PostgreSQL 16 | RLS enabled, SSL required |
| Connection Pool | HikariCP | max=20, tenant-context injection |
| Backup | pg_dump + WAL | Daily + continuous WAL |
| Encryption | AES-256 | Transparent Data Encryption |

**Connection Pool Configuration:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      # NOTE: Tenant context is set per-request via SET LOCAL in TenantContextWebFilter
      # Never use connection-init-sql for tenant - causes cross-tenant data leakage on pooled connections
```

---

## 3. Monitoring & Observability

### 3.1 Monitoring Stack

```
┌─────────────────────────────────────────────────────────────────┐
│                    MONITORING STACK                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│  │   Grafana   │◀───│ Prometheus  │◀───│  DVMM API   │        │
│  │ (Dashboard) │    │  (Metrics)  │    │ (/actuator) │        │
│  └─────────────┘    └─────────────┘    └─────────────┘        │
│         │                                      │               │
│         ▼                                      ▼               │
│  ┌─────────────┐                      ┌─────────────┐         │
│  │    Loki     │◀─────────────────────│  Promtail   │         │
│  │   (Logs)    │                      │ (Log Ship)  │         │
│  └─────────────┘                      └─────────────┘         │
│         │                                                      │
│         ▼                                                      │
│  ┌─────────────┐                                              │
│  │ AlertManager│───▶ PagerDuty / Slack                        │
│  │  (Alerts)   │                                              │
│  └─────────────┘                                              │
│                                                                │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Key Metrics

| Category | Metric | Target | Alert Threshold |
|----------|--------|--------|-----------------|
| **Availability** | Uptime | 99.5% | < 99% |
| **Latency** | API P95 | < 500ms | > 1s |
| **Latency** | API P99 | < 1s | > 2s |
| **Throughput** | Requests/sec | Baseline | +50% spike |
| **Errors** | Error rate | < 1% | > 5% |
| **Saturation** | CPU | < 70% | > 85% |
| **Saturation** | Memory | < 80% | > 90% |
| **Saturation** | DB Connections | < 80% | > 90% |

### 3.3 Logging Strategy

**Structured Logging Format (JSON):**
```json
{
  "timestamp": "2025-01-15T10:23:45.123Z",
  "level": "INFO",
  "logger": "com.eaf.dvmm.api.VmRequestController",
  "message": "VM request created",
  "correlationId": "abc-123-def",
  "tenantId": "tenant-uuid",
  "userId": "user-uuid",
  "requestId": "request-uuid",
  "duration_ms": 145
}
```

**Log Levels:**
| Level | Usage | Retention |
|-------|-------|-----------|
| ERROR | Exceptions, failures | 90 days |
| WARN | Degraded performance, retries | 30 days |
| INFO | Business events, audit | 7 years (audit) |
| DEBUG | Technical details | 7 days (dev only) |

### 3.4 Health Endpoints

```yaml
# Spring Boot Actuator Configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

**Health Check Components:**
- `/actuator/health/liveness` - Is the app running?
- `/actuator/health/readiness` - Can it accept traffic?
- `/actuator/health` - Full health (DB, Keycloak, VMware)

---

## 4. Deployment Strategy

### 4.1 Deployment Approach

| Environment | Strategy | Rollback Time |
|-------------|----------|---------------|
| Dev | Rolling update | N/A |
| Staging | Blue-Green | Instant |
| Production | Blue-Green | < 5 min |

### 4.2 Blue-Green Deployment

```
┌─────────────────────────────────────────────────────────────┐
│                    PRODUCTION                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐         ┌─────────────┐                   │
│  │   Load      │         │   Load      │                   │
│  │  Balancer   │         │  Balancer   │                   │
│  └──────┬──────┘         └──────┬──────┘                   │
│         │                       │                           │
│    ┌────┴────┐             ┌────┴────┐                     │
│    ▼         ▼             ▼         ▼                     │
│ ┌──────┐ ┌──────┐     ┌──────┐ ┌──────┐                   │
│ │ Blue │ │ Blue │     │Green │ │Green │                   │
│ │ v1.0 │ │ v1.0 │     │ v1.1 │ │ v1.1 │                   │
│ │ LIVE │ │ LIVE │     │ IDLE │ │ IDLE │                   │
│ └──────┘ └──────┘     └──────┘ └──────┘                   │
│                                                             │
│  Switch: Update LB → Green becomes LIVE                    │
│  Rollback: Update LB → Blue becomes LIVE again             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 4.3 Database Migrations

**Zero-Downtime Migration Strategy:**

1. **Backward Compatible Changes Only**
   - Add columns with defaults
   - Add indexes concurrently
   - Never drop/rename in same release

2. **Migration Sequence:**
   ```text
   Release N:   Add new_column (nullable)
   Release N+1: Migrate data, make non-null
   Release N+2: Remove old_column
   ```

3. **Flyway Configuration:**
   ```yaml
   spring:
     flyway:
       enabled: true
       locations: classpath:db/migration
       baseline-on-migrate: true
       validate-on-migrate: true
   ```

### 4.4 Feature Flags

```kotlin
// Feature Flag Service
interface FeatureFlags {
    fun isEnabled(feature: String, tenantId: TenantId): Boolean
}

// Usage
if (featureFlags.isEnabled("vmware_v2_api", tenantId)) {
    vmwareV2Client.createVm(spec)
} else {
    vmwareV1Client.createVm(spec)
}
```

**Feature Flag Use Cases:**
- Gradual rollout (10% → 50% → 100%)
- Tenant-specific features (pilot tenants)
- Kill switches for problematic features
- A/B testing

---

## 5. Disaster Recovery

### 5.1 Recovery Objectives

| Metric | Target | NFR Reference |
|--------|--------|---------------|
| **RTO** (Recovery Time Objective) | < 4 hours | NFR-AVAIL-3 |
| **RPO** (Recovery Point Objective) | < 1 hour | NFR-AVAIL-4 |
| **Backup Frequency** | Daily + WAL | NFR-AVAIL-5 |
| **Backup Retention** | 30 days | NFR-AVAIL-6 |

### 5.2 Backup Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    BACKUP STRATEGY                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  PostgreSQL                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    │
│  │   Primary   │───▶│  WAL Stream │───▶│   Archive   │    │
│  │   Database  │    │ (Continuous)│    │  (S3/Blob)  │    │
│  └─────────────┘    └─────────────┘    └─────────────┘    │
│         │                                                   │
│         ▼                                                   │
│  ┌─────────────┐                                           │
│  │  pg_dump    │───▶ Daily full backup (encrypted)         │
│  │  (Daily)    │                                           │
│  └─────────────┘                                           │
│                                                             │
│  Keycloak                                                   │
│  ┌─────────────┐                                           │
│  │   Export    │───▶ Daily realm export                    │
│  │   (Daily)   │                                           │
│  └─────────────┘                                           │
│                                                             │
│  Configuration                                              │
│  ┌─────────────┐                                           │
│  │    Git      │───▶ Infrastructure as Code (versioned)    │
│  │   Repo      │                                           │
│  └─────────────┘                                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 Recovery Procedures

**Scenario 1: Database Corruption**
1. Stop application
2. Restore from latest backup + WAL replay
3. Verify data integrity
4. Restart application
5. **Time: ~2 hours**

**Scenario 2: Complete Infrastructure Loss**
1. Provision new infrastructure (IaC)
2. Restore database from backup
3. Restore Keycloak realm
4. Deploy application
5. Update DNS
6. **Time: ~4 hours**

**Scenario 3: VMware Connection Loss**
1. System enters degraded mode automatically
2. Requests queued for later processing
3. Users see status banner
4. On reconnection: queue processed automatically
5. **Time: Automatic (NFR-AVAIL-11)**

### 5.4 DR Testing

| Test Type | Frequency | Scope |
|-----------|-----------|-------|
| Backup Restore | Monthly | Full DB restore to staging |
| Failover | Quarterly | Switch to standby |
| Full DR | Annual | Complete infrastructure rebuild |

---

## 6. Security Integration

### 6.1 Pipeline Security

| Stage | Security Control |
|-------|-----------------|
| Code | SAST (SonarQube, Detekt) |
| Dependencies | OWASP Dependency-Check |
| Container | Trivy vulnerability scan |
| Secrets | No secrets in code (env/vault) |
| Deploy | RBAC, audit logging |

### 6.2 Infrastructure Security

| Component | Security Measure |
|-----------|-----------------|
| Network | TLS 1.3 everywhere |
| Database | Encrypted at rest, SSL connections |
| Secrets | HashiCorp Vault (Growth) / Environment (MVP) |
| Access | RBAC, MFA for admin access |
| Audit | All changes logged |

---

## 7. Operational Runbooks

### 7.1 Incident Response

```
┌─────────────────────────────────────────────────────────────┐
│                 INCIDENT RESPONSE                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Alert Triggered                                            │
│       │                                                     │
│       ▼                                                     │
│  ┌─────────────┐                                           │
│  │   Triage    │  Classify: P1/P2/P3/P4                    │
│  └──────┬──────┘                                           │
│         │                                                   │
│    P1/P2│         P3/P4                                    │
│         │           │                                       │
│         ▼           ▼                                       │
│  ┌─────────────┐  ┌─────────────┐                          │
│  │  Immediate  │  │   Queue     │                          │
│  │  Response   │  │   for Fix   │                          │
│  └──────┬──────┘  └─────────────┘                          │
│         │                                                   │
│         ▼                                                   │
│  ┌─────────────┐                                           │
│  │  Mitigate   │  Rollback / Workaround                    │
│  └──────┬──────┘                                           │
│         │                                                   │
│         ▼                                                   │
│  ┌─────────────┐                                           │
│  │   Resolve   │  Fix root cause                           │
│  └──────┬──────┘                                           │
│         │                                                   │
│         ▼                                                   │
│  ┌─────────────┐                                           │
│  │  Post-     │  Document, improve                         │
│  │  Mortem    │                                            │
│  └─────────────┘                                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 Common Runbooks

| Scenario | Runbook |
|----------|---------|
| High CPU | Scale up, check queries, profile |
| High Memory | Check for leaks, restart pod |
| DB Connection Exhaustion | Check pool config, kill idle |
| VMware Timeout | Check network, retry queue |
| Auth Failure | Check Keycloak health |
| Deployment Failure | Rollback, check logs |

---

## 8. Development Environment

### 8.1 Local Setup

```bash
# Prerequisites
- JDK 21
- Docker Desktop
- Node.js 20+
- IntelliJ IDEA (recommended)

# Quick Start
git clone https://github.com/org/eaf.git
cd eaf
./gradlew build
docker-compose up -d
./gradlew bootRun
```

### 8.2 Testcontainers Configuration

```kotlin
// Shared containers (singleton)
object TestContainers {
    val postgres = PostgreSQLContainer("postgres:16")
        .withDatabaseName("dvmm_test")
        .withUsername("test")
        .withPassword("test")

    val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:26.0")
        .withRealmImportFile("test-realm.json")

    val vcsim = GenericContainer("vmware/vcsim:latest")
        .withExposedPorts(443)

    init {
        postgres.start()
        keycloak.start()
        vcsim.start()
    }
}
```

---

## Appendix A: NFR Traceability

| NFR ID | Requirement | Section |
|--------|-------------|---------|
| NFR-MAINT-1 | Test coverage ≥70% | 1.3 |
| NFR-MAINT-2 | Mutation score ≥70% | 1.3 |
| NFR-MAINT-4 | CI/CD pipeline | 1.1 |
| NFR-MAINT-6 | Rollback < 15 min | 4.1 |
| NFR-MAINT-8 | Zero-downtime migrations | 4.3 |
| NFR-MAINT-11 | E2E tests < 15 min | 1.2 |
| NFR-AVAIL-1 | Uptime 99.5% | 3.2 |
| NFR-AVAIL-3 | RTO < 4 hours | 5.1 |
| NFR-AVAIL-4 | RPO < 1 hour | 5.1 |
| NFR-AVAIL-5 | Daily + WAL backup | 5.2 |
| NFR-AVAIL-7 | Health endpoints | 3.4 |
| NFR-OBS-1 | Structured logging | 3.3 |
| NFR-OBS-2 | Correlation IDs | 3.3 |
| NFR-OBS-3 | Prometheus metrics | 3.1 |
| NFR-OBS-4 | Error alerting | 3.1 |
| NFR-OBS-5 | Log aggregation | 3.1 |
| NFR-OBS-6 | Grafana dashboards | 3.1 |

---

*This DevOps Strategy document is part of the DVMM Enterprise Method documentation.*
