# Story 3.11: Custom ppc64le Keycloak Docker Image

**Epic:** Epic 3 - Authentication & Authorization
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR006 (Multi-Architecture Support)

---

## User Story

As a framework developer,
I want custom Keycloak Docker image built for ppc64le architecture,
So that EAF supports all required processor architectures (amd64, arm64, ppc64le).

---

## Acceptance Criteria

1. ✅ docker/keycloak/Dockerfile.ppc64le created (UBI9-based build)
2. ✅ Multi-stage build: UBI9 → Maven build → Runtime image
3. ✅ Build script: scripts/build-keycloak-ppc64le.sh automates image creation
4. ✅ Image tested on ppc64le emulation (QEMU) or real hardware if available
5. ✅ Image pushed to container registry with tag: keycloak:26.4.2-ppc64le
6. ✅ docker-compose.ppc64le.yml variant uses custom image
7. ✅ Build process documented in docs/reference/multi-arch-builds.md
8. ✅ Quarterly rebuild schedule documented (align with Keycloak releases)

---

## Prerequisites

**Story 3.1** - Spring Security OAuth2 Resource Server Foundation

---

## References

- PRD: FR006
- Architecture: ADR-002 (Multi-Architecture Support), Section 10
- Tech Spec: Section 2.1 (Keycloak 26.4.2)
