# Story 3.11: Custom ppc64le Keycloak Docker Image

**Epic:** Epic 3 - Authentication & Authorization
**Status:** ready-for-dev
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

## Tasks/Subtasks

### Task 1: Create Dockerfile.ppc64le
- [ ] Create docker/keycloak/Dockerfile.ppc64le with UBI9 base image
- [ ] Implement multi-stage build (UBI9 → Maven build → Runtime image)
- [ ] Configure Keycloak 26.4.2 build from source
- [ ] Optimize image size (remove build artifacts in final stage)

### Task 2: Create build automation script
- [ ] Create scripts/build-keycloak-ppc64le.sh
- [ ] Add command-line parameters (version, registry, tag)
- [ ] Document script usage and prerequisites
- [ ] Make script executable and add error handling

### Task 3: Test image on ppc64le
- [ ] Test build on QEMU ppc64le emulation OR real hardware if available
- [ ] Verify Keycloak starts successfully
- [ ] Test OIDC endpoints (/.well-known/openid-configuration)
- [ ] Test realm import functionality

### Task 4: Push image to container registry
- [ ] Build and tag image as keycloak:26.4.2-ppc64le
- [ ] Configure registry authentication
- [ ] Push image to container registry
- [ ] Verify image is pullable from registry

### Task 5: Create docker-compose variant
- [ ] Create docker-compose.ppc64le.yml
- [ ] Configure to use custom ppc64le image
- [ ] Test complete stack startup (PostgreSQL + Keycloak + Redis)
- [ ] Verify network connectivity between services

### Task 6: Document build process and maintenance
- [ ] Create docs/reference/multi-arch-builds.md
- [ ] Document UBI9 base image selection rationale
- [ ] Document Maven build process
- [ ] Document quarterly rebuild schedule (align with Keycloak releases)
- [ ] Document troubleshooting steps (QEMU, registry issues)

---

## Prerequisites

**Story 3.1** - Spring Security OAuth2 Resource Server Foundation

---

## Dev Agent Record

### Context Reference
- Story Context: docs/sprint-artifacts/epic-3/3-11-keycloak-ppc64le.context.xml

### Debug Log
_To be filled during implementation_

### Completion Notes
_To be filled when story is complete_

---

## File List

_To be updated during implementation_

---

## Change Log

- 2025-11-16: Story context generated, status updated to ready-for-dev

---

## References

- PRD: FR006
- Architecture: ADR-002 (Multi-Architecture Support), Section 10
- Tech Spec: Section 2.1 (Keycloak 26.4.2)
