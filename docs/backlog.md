# Engineering Backlog

This backlog collects cross-cutting or future action items that emerge from reviews and planning.

Routing guidance:

- Use this file for non-urgent optimizations, refactors, or follow-ups that span multiple stories/epics.
- Must-fix items to ship a story belong in that story’s `Tasks / Subtasks`.
- Same-epic improvements may also be captured under the epic Tech Spec `Post-Review Follow-ups` section.

| Date | Story | Epic | Type | Severity | Owner | Status | Notes |
| ---- | ----- | ---- | ---- | -------- | ----- | ------ | ----- |
| 2025-11-08 | Epic 2 Retro | 3 | Research | High | Elena | Open | Keycloak Testcontainer research before Epic 3 Story 1. Investigate: Keycloak 26.4.2 Testcontainer setup, JWT token generation in tests, realm configuration, multi-arch support (amd64/arm64/ppc64le). Estimated: 4 hours. Critical for Story 3.10 (Testcontainers Keycloak) approach. |
| 2025-11-08 | Epic 2 Retro | 3 | Knowledge | Medium | Team | Open | Spring Security OAuth2 Resource Server knowledge development session before Epic 3 Story 1. Topics: Spring Security 6.x architecture, JWT validation flow, role extraction, security context propagation, testing patterns. Estimated: 2 hours. Preparation for Stories 3.1-3.5. |
| 2025-11-08 | Epic 2 Retro | 3 | Validation | Low | Charlie | Open | Verify aggregate caching (WeakReferenceCache) production readiness before Epic 3. Review: GC behavior under load, memory consumption patterns, cache hit/miss metrics, thread safety. Estimated: 1 hour. Validates Epic 2 Story 2.13 optimization in production context. |
| 2025-11-08 | Epic 2 Retro | - | Documentation | Low | Wall-E | Open | Document Story 2.13 performance optimization journey (70x improvement) as blog post or knowledge base article. Content: Problem (6s/cmd), investigation (8h debugging), solution (4-vector optimization), result (4ms/cmd). Value: Team learning, recruitment material, conference talk potential. Estimated: 3 hours. Optional. |
| 2025-11-07 | 2.12 | 3 | Enhancement | Low | TBD | Open | Add integration test for OpenAPI endpoints (`/v3/api-docs`, `/swagger-ui.html`) in Epic 3 after authentication is implemented. Suggested test: `@SpringBootTest` verifying OpenAPI bean registration and endpoint accessibility with JWT authentication. Reference: CodeRabbit AI review suggestion on PR #34. |
| 2025-11-07 | 2.12 | 3 | Configuration | Low | TBD | Open | Epic 3 security configuration must include `permitAll()` for Swagger UI paths (`/swagger-ui/**`, `/v3/api-docs/**`) to avoid blocking documentation access. Document in Story 3.1 (Spring Security OAuth2 configuration). |
| 2025-11-05 | 2.3 | 2 | Bug | High | TBD | Closed (2025-11-05) | Restored event store uniqueness via triggers and lookup index (`framework/persistence/src/main/resources/db/migration/V002__partitioning_setup.sql:120-179`) |
| 2025-11-05 | 2.3 | 2 | Bug | High | TBD | Closed (2025-11-05) | Reintroduced aggregate replay B-tree index (`framework/persistence/src/main/resources/db/migration/V003__brin_indexes.sql:9-15`) |
| 2025-11-05 | 2.3 | 2 | Documentation | Medium | TBD | Closed (2025-11-05) | Story metadata/DoD realigned with in-progress state (`docs/stories/epic-2/story-2.3-event-store-partitioning.md:1-184`) |
| 2025-11-05 | 2.3 | 2 | Documentation | Medium | TBD | Closed (2025-11-05) | Reference doc updated with integrity enforcement notes (`docs/reference/event-store-optimization.md:1-38`) |
| 2025-11-05 | 2.3 | 2 | Security | Medium | TBD | Closed (2025-11-05) | Partition script validates identifiers before executing SQL (`scripts/create-event-store-partition.sh:10-74`) |
