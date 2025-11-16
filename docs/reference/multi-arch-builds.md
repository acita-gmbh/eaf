# Multi-Architecture Docker Builds

**Version:** 1.0.0
**Last Updated:** 2025-11-16
**Maintainer:** EAF Team

---

## Overview

This document describes the process for building and maintaining custom Docker images for multiple processor architectures in the EAF project, with specific focus on the ppc64le Keycloak image.

### Supported Architectures

| Architecture | Status | Official Support | Custom Build Required |
|--------------|--------|------------------|----------------------|
| **amd64** (x86_64) | ✅ Production | Yes (all images) | No |
| **arm64** (aarch64) | ✅ Production | Yes (most images) | No |
| **ppc64le** | ✅ Supported | Keycloak: No, Others: Yes | Yes (Keycloak only) |

### Why Multi-Architecture Support?

- **Framework Reusability:** EAF must deploy on diverse enterprise infrastructures
- **Future-Proofing:** €4.4K investment documented in architecture.md
- **Customer Requirements:** ZEWSSP uses amd64, future products may require ppc64le
- **No Official ppc64le Images:** Keycloak project does not provide official ppc64le builds

---

## Custom Keycloak ppc64le Image

### Quick Start

```bash
# 1. Build the custom image
./scripts/build-keycloak-ppc64le.sh

# 2. Test locally (QEMU emulation)
docker run --platform linux/ppc64le -p 8080:8080 keycloak:26.4.2-ppc64le start-dev

# 3. Verify health
curl http://localhost:8080/health

# 4. Use in docker-compose
docker-compose -f docker-compose.ppc64le.yml up -d
```

### Build Architecture

**Multi-Stage Dockerfile:** `docker/keycloak/Dockerfile.ppc64le`

```
Stage 1: Build (UBI9 + Maven)
  ├─ Base: registry.access.redhat.com/ubi9/ubi:9.3
  ├─ Install: Java 21, Maven, wget
  ├─ Download: Keycloak 26.4.2 source distribution
  └─ Output: /build/keycloak

Stage 2: Runtime (UBI9 OpenJDK-21)
  ├─ Base: registry.access.redhat.com/ubi9/openjdk-21:1.19
  ├─ Copy: Keycloak from build stage
  ├─ Configure: User, permissions, health checks
  └─ Size Target: <500MB final image
```

### Base Image Selection Rationale

**Why Red Hat Universal Base Image 9 (UBI9)?**

1. **Official ppc64le Support:** Red Hat provides native ppc64le builds
2. **Long-Term Maintenance:** UBI9 receives security updates until 2032
3. **Enterprise-Grade:** Production-proven in RHEL ecosystems
4. **Free Distribution:** No license required for UBI images
5. **Multi-Arch Consistency:** Same base across amd64, arm64, ppc64le

**Alternatives Considered:**

| Base Image | ppc64le Support | Rejected Reason |
|------------|----------------|-----------------|
| Alpine Linux | Limited | Missing packages, compatibility issues |
| Ubuntu | Yes | Larger image size, less enterprise focus |
| Debian | Yes | UBI9 preferred for enterprise support |
| Official Keycloak Image | **No** | Not available for ppc64le |

---

## Build Process

### Prerequisites

Install required tools:

```bash
# Docker with buildx support
docker --version  # 20.10+ required

# Enable buildx (if not default)
docker buildx create --use

# Install QEMU for cross-platform emulation
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Verify ppc64le support
docker buildx inspect | grep "linux/ppc64le"
```

### Build Script Usage

**Script:** `scripts/build-keycloak-ppc64le.sh`

```bash
# Basic build (default: keycloak:26.4.2-ppc64le)
./scripts/build-keycloak-ppc64le.sh

# Build and push to GitHub Container Registry
./scripts/build-keycloak-ppc64le.sh \
  --registry ghcr.io/acita-gmbh \
  --push

# Build specific version for custom registry
./scripts/build-keycloak-ppc64le.sh \
  --version 26.4.3 \
  --registry localhost:5000 \
  --tag keycloak:custom-ppc64le \
  --push

# Dry run (show commands without executing)
./scripts/build-keycloak-ppc64le.sh --dry-run

# Help
./scripts/build-keycloak-ppc64le.sh --help
```

### Manual Build (Without Script)

```bash
# Navigate to project root
cd /path/to/eaf

# Build image
docker buildx build \
  --platform linux/ppc64le \
  --build-arg KEYCLOAK_VERSION=26.4.2 \
  -f docker/keycloak/Dockerfile.ppc64le \
  -t keycloak:26.4.2-ppc64le \
  --load \
  .

# Verify build
docker images keycloak:26.4.2-ppc64le
```

---

## Testing on ppc64le

### Option 1: QEMU Emulation (Linux Only)

**⚠️ macOS/Docker Desktop Limitation:** QEMU ppc64le emulation does **not work** on macOS/Docker Desktop due to containerd-shim compatibility issues. Use **Linux** or **GitHub Actions CI** for ppc64le builds.

**Setup QEMU (Linux only):**

```bash
# Install QEMU emulation
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Verify emulation works
docker run --platform linux/ppc64le --rm alpine uname -m
# Expected output: ppc64le
```

**Known Issue on macOS:**
```
ERROR: fork/exec /usr/bin/unpigz: exec format error
```
**Solution:** Use GitHub Actions CI (`.github/workflows/multi-arch-build.yml`) which runs on Linux.

**Test Keycloak Image:**

```bash
# Start Keycloak in development mode
docker run --platform linux/ppc64le \
  -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  keycloak:26.4.2-ppc64le \
  start-dev

# Verify health (in another terminal)
curl http://localhost:8080/health
# Expected: {"status":"UP", ...}

# Test OIDC discovery endpoint
curl http://localhost:8080/realms/master/.well-known/openid-configuration
# Expected: JSON configuration

# Access admin console
open http://localhost:8080/admin
# Login: admin / admin
```

**Performance Note:** QEMU emulation is **significantly slower** than native execution (5-10x slower startup). Expect:
- Keycloak startup: 2-5 minutes (vs. 30-60 seconds native)
- Admin console: Slower response times
- This is **acceptable for testing**, not production

### Option 2: Real ppc64le Hardware (Production Validation)

If real ppc64le hardware is available:

```bash
# Copy image to ppc64le host
docker save keycloak:26.4.2-ppc64le | ssh ppc64le-host docker load

# SSH to ppc64le host
ssh ppc64le-host

# Run natively (much faster!)
docker run -p 8080:8080 keycloak:26.4.2-ppc64le start-dev

# Verify architecture
docker run --rm keycloak:26.4.2-ppc64le uname -m
# Expected: ppc64le
```

---

## Pushing to Container Registry

### GitHub Container Registry (ghcr.io)

```bash
# 1. Authenticate to GitHub Container Registry
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# 2. Build and push
./scripts/build-keycloak-ppc64le.sh \
  --registry ghcr.io/acita-gmbh \
  --push

# 3. Verify image is available
docker pull ghcr.io/acita-gmbh/keycloak:26.4.2-ppc64le
```

### Docker Hub

```bash
# 1. Authenticate to Docker Hub
docker login

# 2. Build and tag for Docker Hub
./scripts/build-keycloak-ppc64le.sh \
  --registry docker.io/acita-eaf \
  --tag keycloak:26.4.2-ppc64le \
  --push

# 3. Verify
docker pull acita-eaf/keycloak:26.4.2-ppc64le
```

### Private Registry

```bash
# 1. Set up local registry (for testing)
docker run -d -p 5000:5000 --name registry registry:2

# 2. Build and push to local registry
./scripts/build-keycloak-ppc64le.sh \
  --registry localhost:5000 \
  --push

# 3. Verify
docker pull localhost:5000/keycloak:26.4.2-ppc64le
```

---

## Using in docker-compose

**File:** `docker-compose.ppc64le.yml`

```yaml
services:
  keycloak:
    image: keycloak:26.4.2-ppc64le  # Custom ppc64le image
    platform: linux/ppc64le
    # ... rest of configuration
```

**Start stack:**

```bash
# Build custom image first (if not already built)
./scripts/build-keycloak-ppc64le.sh

# Start all services
docker-compose -f docker-compose.ppc64le.yml up -d

# Verify all services healthy
docker-compose -f docker-compose.ppc64le.yml ps

# View logs
docker-compose -f docker-compose.ppc64le.yml logs -f keycloak

# Stop stack
docker-compose -f docker-compose.ppc64le.yml down
```

---

## Quarterly Rebuild Schedule

**Critical:** Keycloak ppc64le image **MUST** be rebuilt quarterly to incorporate security patches and align with Keycloak release cycle.

### Rebuild Trigger Events

1. **Keycloak Minor/Patch Release** (Monthly cadence)
   - Track: https://www.keycloak.org/downloads
   - Example: 26.4.2 → 26.4.3

2. **Security Vulnerabilities** (Immediate)
   - Monitor: Keycloak Security Advisories
   - CVE CVSS ≥7.0: Rebuild within 48 hours

3. **Scheduled Quarterly Rebuild** (Proactive)
   - Q1: January 15
   - Q2: April 15
   - Q3: July 15
   - Q4: October 15

### Rebuild Process

```bash
# 1. Update Keycloak version in build script
vim scripts/build-keycloak-ppc64le.sh
# Change: DEFAULT_VERSION="26.4.2" → DEFAULT_VERSION="26.4.3"

# 2. Update Dockerfile ARG (optional, uses build script default)
vim docker/keycloak/Dockerfile.ppc64le
# Change: ARG KEYCLOAK_VERSION=26.4.2 → ARG KEYCLOAK_VERSION=26.4.3

# 3. Rebuild image
./scripts/build-keycloak-ppc64le.sh --version 26.4.3

# 4. Test new image
docker run --platform linux/ppc64le -p 8080:8080 keycloak:26.4.3-ppc64le start-dev
# Verify: curl http://localhost:8080/health

# 5. Update docker-compose
vim docker-compose.ppc64le.yml
# Change: image: keycloak:26.4.2-ppc64le → image: keycloak:26.4.3-ppc64le

# 6. Test full stack
docker-compose -f docker-compose.ppc64le.yml up -d

# 7. Push to registry
./scripts/build-keycloak-ppc64le.sh --version 26.4.3 --registry ghcr.io/acita-gmbh --push

# 8. Tag in git
git tag -a keycloak-ppc64le-26.4.3 -m "Quarterly rebuild: Keycloak 26.4.3 ppc64le image"
git push origin keycloak-ppc64le-26.4.3
```

### Keycloak Release Calendar Alignment

| Keycloak Version | Release Date | ppc64le Rebuild Target |
|------------------|--------------|------------------------|
| 26.4.2 | 2025-10-30 | 2025-11-16 (Initial) |
| 26.4.3 | 2025-11-?? | Within 2 weeks of release |
| 27.0.0 | 2026-Q1 | January 15, 2026 |

**Subscription:** Subscribe to Keycloak GitHub releases:
- https://github.com/keycloak/keycloak/releases
- Watch → Custom → Releases

---

## Troubleshooting

### QEMU Issues

**Problem:** `exec user process caused: exec format error`

**Cause:** QEMU emulation not installed or not enabled

**Solution:**
```bash
# Reset QEMU
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Verify support
docker buildx inspect | grep linux/ppc64le
```

---

**Problem:** Build extremely slow (>30 minutes)

**Cause:** QEMU emulation overhead

**Solution:**
- **Expected behavior** - QEMU is 5-10x slower
- Use `--load` to cache locally, avoid rebuilds
- Consider using real ppc64le hardware for production builds

---

### Registry Issues

**Problem:** `unauthorized: authentication required`

**Cause:** Not logged into container registry

**Solution:**
```bash
# GitHub Container Registry
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# Docker Hub
docker login

# Private registry
docker login registry.example.com
```

---

**Problem:** `denied: requested access to the resource is denied`

**Cause:** Insufficient registry permissions

**Solution:**
- Verify repository exists and you have push access
- Check organization/team permissions for ghcr.io
- For GitHub: Enable "Write" permission for packages

---

### Build Failures

**Problem:** `ERROR: failed to solve: failed to fetch UBI9 image`

**Cause:** Red Hat registry rate limiting or network issues

**Solution:**
```bash
# Pre-pull base images
docker pull registry.access.redhat.com/ubi9/ubi:9.3
docker pull registry.access.redhat.com/ubi9/openjdk-21:1.19

# Retry build
./scripts/build-keycloak-ppc64le.sh
```

---

**Problem:** `wget: unable to resolve host address 'github.com'`

**Cause:** DNS resolution failure in build container

**Solution:**
```bash
# Use Docker DNS
docker build --add-host github.com:140.82.121.3 ...

# Or configure Docker daemon DNS
sudo vim /etc/docker/daemon.json
{
  "dns": ["8.8.8.8", "1.1.1.1"]
}
sudo systemctl restart docker
```

---

### Runtime Issues

**Problem:** Keycloak fails to start with database connection errors

**Cause:** PostgreSQL not ready or wrong credentials

**Solution:**
```bash
# Check PostgreSQL health
docker-compose -f docker-compose.ppc64le.yml ps postgres
# Should show "healthy"

# Verify credentials match
cat .env | grep POSTGRES
# Ensure KC_DB_USERNAME and KC_DB_PASSWORD match POSTGRES_USER and POSTGRES_PASSWORD

# Check logs
docker-compose -f docker-compose.ppc64le.yml logs postgres keycloak
```

---

**Problem:** Realm import fails silently

**Cause:** realm-export.json not mounted correctly

**Solution:**
```bash
# Verify volume mount
docker inspect eaf-keycloak-ppc64le | grep realm-export
# Should show: "Source": "./docker/keycloak/realm-export.json"

# Check file exists
ls -la docker/keycloak/realm-export.json

# Verify realm imported
docker-compose -f docker-compose.ppc64le.yml logs keycloak | grep "imported"
```

---

## GitHub Actions CI Integration

**Workflow:** `.github/workflows/multi-arch-build.yml`

**Triggers:**
- Pull requests modifying ppc64le-related files
- Manual workflow dispatch (with optional registry push)

**What it does:**
1. Sets up QEMU on Linux (ubuntu-latest)
2. Builds Keycloak ppc64le image using build script
3. Verifies image architecture and size
4. Validates docker-compose.ppc64le.yml syntax
5. (Optional) Pushes to GitHub Container Registry

**Manual Trigger:**
```bash
# Via GitHub UI: Actions → Multi-Architecture Build → Run workflow
# Or via gh CLI:
gh workflow run multi-arch-build.yml
gh workflow run multi-arch-build.yml -f push_to_registry=true
```

**CI will automatically run** when you push changes to:
- `docker/keycloak/Dockerfile.ppc64le`
- `scripts/build-keycloak-ppc64le.sh`
- `docker-compose.ppc64le.yml`

---

## Related Documentation

- **Architecture:** `docs/architecture.md` - Section 10 (Multi-Architecture Support)
- **PRD:** `docs/PRD.md` - FR006 (Multi-Architecture Requirements)
- **Tech Spec:** `docs/tech-spec-epic-3.md` - Story 3.11 (Keycloak ppc64le)
- **Keycloak Setup:** `docker/keycloak/README.md` - Realm configuration
- **Story Context:** `docs/sprint-artifacts/epic-3/3-11-keycloak-ppc64le.context.xml`

---

## Support & Maintenance

**Maintainer:** EAF Team
**Support:** Contact via GitHub Issues or Slack #eaf-support
**Security:** Report vulnerabilities to security@axians.com

**Rebuild Reminders:**
- Set calendar reminders for quarterly rebuilds (Jan 15, Apr 15, Jul 15, Oct 15)
- Subscribe to Keycloak GitHub releases for security advisories
- Monitor Docker Hub for UBI9 base image updates

---

**Version History:**

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-16 | Initial documentation for Keycloak 26.4.2 ppc64le build |
