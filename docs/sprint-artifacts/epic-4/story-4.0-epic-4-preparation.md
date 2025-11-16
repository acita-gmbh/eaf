# Story 4.0: Epic 4 Preparation - Process, Context, and Research

Status: ready-for-dev

## Story

As the development team,
We want comprehensive Epic 4 preparation including process improvements, story regeneration, and technical research,
So that Epic 4 (Multi-Tenancy) starts with fresh context, documented patterns, and validated prerequisites.

## Acceptance Criteria

1. ✅ All Epic 2 outstanding action items completed (2 items)
2. ✅ Continuous Story Context process documented and implemented
3. ✅ All 10 Epic 4 stories regenerated with current template format
4. ✅ Story Context XML generated for all 10 Epic 4 stories
5. ✅ Batch story regeneration automation created
6. ✅ Axon ThreadLocal propagation patterns researched and documented
7. ✅ LitmusKt framework setup completed for early concurrency testing
8. ✅ PostgreSQL RLS research completed (Elena knowledge transfer)
9. ✅ OWASP security research documented for future stories
10. ✅ All preparation work validated and tested

## Tasks / Subtasks

- [ ] **Phase 1: Process Improvements (Epic 2 Outstanding)**
  - [ ] Add "Axon Testing Patterns" section to architecture.md (Charlie)
    - Document mode=subscribing for tests
    - Document @ServiceConnection timing with @EnableMethodSecurity
    - Document Container as Spring @Bean pattern
    - Document PropagatingErrorHandler and aggregate caching
  - [ ] Create Story Definition of Done checklist (Bob)
    - Include: Continuous Story Context regeneration after each story
    - Include: AI review step before marking complete
    - Include: All quality gates (ktlint, Detekt, tests pass 100%)
    - Save to: docs/story-definition-of-done.md

- [ ] **Phase 2: Continuous Context Process (Epic 3 Finding)**
  - [ ] Document Continuous Story Context in CONTRIBUTING.md (Bob)
    - Process: After Story X.Y completes → regenerate context for Story X.(Y+1)
    - Automation: Workflow reminder or Git hook suggestion
    - Rationale: Prevents context drift observed in Epic 3 Stories 3.7-3.10
  - [ ] Add continuous context to Story DoD checklist
    - Ensure consistency across documentation

- [ ] **Phase 3: Story Preparation (Critical Blockers)**
  - [ ] Regenerate all 10 Epic 4 stories with current template (Bob)
    - Stories 4.1-4.10: Add Tasks/Subtasks, Dev Agent Record, File List, Change Log
    - Preserve existing ACs and story content
    - Use create-story workflow with #yolo mode
  - [ ] Generate Story Context XML for all 10 Epic 4 stories (Bob)
    - Run story-context workflow for Stories 4.1-4.10
    - Include Epic 3 JWT patterns, test configurations, security patterns
    - Fresh ThreadLocal, async propagation, RLS context
  - [ ] Create batch regeneration automation script (Charlie)
    - Script: scripts/regenerate-epic-stories.sh
    - Input: epic number
    - Runs: create-story + story-context for each story in epic
    - Reusable for Epic 5, 6, 7+
    - Include error handling and progress reporting

- [ ] **Phase 4: Technical Research (Epic 4 Prerequisites)**
  - [ ] Research Axon ThreadLocal Propagation Patterns (Charlie, ~4h)
    - Investigate MessageHandlerInterceptor for context propagation
    - Prototype ThreadLocal → async event processor pattern
    - Document in architecture.md "Multi-Tenancy" section
    - Prevents cross-tenant leaks in Story 4.5
  - [ ] Setup LitmusKt Framework Early (Dana + Charlie, ~3h)
    - Extract LitmusKt dependency from Story 4.10 scope
    - Add to framework/multi-tenancy test dependencies
    - Create example concurrency test for TenantContext
    - Document LitmusKt patterns for Stories 4.1, 4.3, 4.5
    - Enable race condition detection from Story 4.1 (not Story 4.10)
  - [ ] PostgreSQL RLS Research & Documentation (Charlie + Elena pair, ~2h)
    - Research Row-Level Security policies and current_setting() pattern
    - Create examples for tenant isolation (widget_view table)
    - Document in architecture.md for Story 4.4 reference
    - Pair programming session (knowledge transfer to Elena)

- [ ] **Phase 5: Security Research (OWASP Preparation)**
  - [ ] Document SSRF Protection requirements (Charlie)
    - Based on OWASP A01:2025 (SSRF in Broken Access Control)
    - Capture key patterns for future implementation
    - Reference: Closed PR #92 insights
  - [ ] Research Circuit Breaker patterns (Charlie, ~2h)
    - Resilience4j overview and Spring Boot integration
    - Prepare for Epic 5+ exception handling stories
    - Based on OWASP A10:2025 (Exception Handling)

## Dev Notes

### Epic 3 Retrospective Findings

**Context Drift (Stories 3.7-3.10):**
- Story Context XML became stale after 6-7 stories
- Caused 9.5h investigation (Story 3.10) and multiple iterations (Story 3.7)
- Solution: Continuous context regeneration after each story

**Epic 2 Action Items (0 of 2 completed):**
- Axon Testing Patterns doc missing → contributed to test config struggles
- Story DoD checklist missing → inconsistent completion standards

**OWASP Security Findings (PR #92 - Closed):**
- PR too large (13K additions, 44 files)
- Build failures (Detekt, ktlint, compilation)
- Critical gaps identified: Supply Chain (65/100), Exception Handling (60/100)
- Approach: Address via focused stories post-Epic 4, not monolithic PR

### Story 4.0 Purpose

Story 4.0 serves as a **Preparation Gate** before Epic 4:
1. Prevents context drift repetition (Epic 3 lesson)
2. Ensures dev agent can execute stories (template format)
3. Provides fresh context with Epic 3 learnings
4. Validates technical prerequisites (ThreadLocal, LitmusKt, PostgreSQL RLS)
5. Establishes continuous improvement processes

**Value Proposition:**
- Investment: Multi-day preparation
- Return: Prevents 20+ hours rework across 10 Epic 4 stories
- Quality: All stories start with comprehensive, current context

### Architecture Context

**Epic 4 Dependencies:**
- JWT tenant_id claim validation (Story 3.5)
- Complete authentication infrastructure (Epic 3)
- Keycloak integration and test infrastructure
- Security patterns and validation layers

**Technical Challenges:**
- ThreadLocal propagation to async Axon event processors (cross-tenant leak risk)
- PostgreSQL Row-Level Security (new territory for team)
- Concurrency testing requirements (race condition detection)
- Spring Boot test configuration complexity (from Epic 3 experience)

### References

- [Epic 3 Retrospective](../retrospectives/epic-3-retro-2025-11-16.md)
- [Epic 2 Retrospective](../retrospectives/epic-2-retro-2025-11-08.md)
- [Architecture: Multi-Tenancy Decision #2](../../architecture.md#multi-tenancy)
- [OWASP Top 10:2025](https://owasp.org/Top10/) - A01, A03, A10

## Dev Agent Record

### Context Reference

- Story Context XML: `docs/sprint-artifacts/epic-4/4-0-epic-4-preparation.context.xml` (Generated: 2025-11-16)

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

### Completion Notes List

### File List

### Change Log

- 2025-11-16: Story 4.0 created from Epic 3 Retrospective findings (Bob)
