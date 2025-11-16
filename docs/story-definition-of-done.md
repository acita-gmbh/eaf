# Story Definition of Done (DoD) Checklist

**Author:** Bob (Scrum Master)
**Date:** 2025-11-16
**Purpose:** Ensure consistent story completion standards across all EAF development

## Mandatory Criteria

A story is only considered **DONE** when ALL criteria below are satisfied:

### 1. Acceptance Criteria ✅

- [ ] All acceptance criteria (ACs) from story file are satisfied
- [ ] Each AC verified with evidence (tests, screenshots, logs)
- [ ] Quantitative thresholds met (if specified in ACs)
- [ ] No deviations from original ACs without explicit approval

### 2. Implementation Quality ✅

- [ ] All tasks and subtasks marked complete `[x]` in story file
- [ ] Code follows EAF coding standards (zero violations)
  - Explicit imports only (no wildcards)
  - Specific exceptions only (no generic catches except infrastructure interceptors)
  - Kotest only (JUnit forbidden)
  - Version catalog for all dependencies
- [ ] Architecture compliance verified (Konsist passing)
- [ ] No technical debt introduced without documentation

### 3. Testing - Constitutional TDD ✅

- [ ] **ALL tests pass 100%** (zero failures, zero skipped)
- [ ] Test-first development followed (tests before implementation)
- [ ] 7-Layer Defense coverage appropriate for story:
  - Layer 1: Static Analysis (ktlint, Detekt, Konsist) - MANDATORY
  - Layer 2: Unit Tests (Kotest, Nullable Pattern) - MANDATORY
  - Layer 3: Integration Tests (Testcontainers) - If applicable
  - Layer 4: Property Tests (Kotest Property) - If applicable (nightly)
  - Layer 5: Fuzz Tests (Jazzer) - If security-critical (nightly)
  - Layer 6: Mutation Tests (Pitest) - If critical path (nightly)
  - Layer 7: Concurrency Tests (LitmusKt) - If concurrent logic (nightly)
- [ ] Line coverage ≥ 85% for new code
- [ ] Regression tests pass (no existing tests broken)
- [ ] Test execution time acceptable:
  - Unit tests: <30 seconds
  - Integration tests: <3 minutes
  - Full suite: <15 minutes

### 4. Quality Gates ✅

- [ ] ktlint passes (zero formatting violations)
- [ ] Detekt passes (zero code smell violations)
- [ ] Konsist passes (zero architecture violations)
- [ ] Build succeeds: `./gradlew clean build`
- [ ] Git pre-commit hooks pass
- [ ] Git pre-push hooks pass (if configured)

### 5. Documentation ✅

- [ ] Story file updated:
  - Status set to `review`
  - Dev Agent Record → Debug Log populated
  - Dev Agent Record → Completion Notes added
  - File List includes all changed files
  - Change Log entry added with date
- [ ] Code comments added for non-obvious logic
- [ ] API documentation updated (if public APIs changed)
- [ ] README updated (if user-facing changes)
- [ ] Architecture.md updated (if architectural decisions made)

### 6. Story Context Maintenance ✅

**CRITICAL: Continuous Story Context Regeneration (Epic 3 Lesson)**

- [ ] **After completing Story X.Y → regenerate context for Story X.(Y+1)**
- [ ] Run `story-context` workflow for next story in epic
- [ ] Verify context includes learnings from completed story
- [ ] Prevents context drift (Epic 3 Stories 3.7-3.10 issue)

**Rationale:** Story Context XML becomes stale after 6-7 stories, causing significant rework. Continuous regeneration ensures fresh patterns and prevents 9.5h investigations like Story 3.10.

### 7. Code Review (AI-Assisted) ✅

- [ ] AI code review completed (`code-review` workflow)
- [ ] All review findings addressed or documented
- [ ] High-severity issues resolved before marking done
- [ ] Review notes added to story file

### 8. Git & CI/CD ✅

- [ ] Feature branch created from main
- [ ] Commits follow convention: `[Story X.Y] Description`
- [ ] Commit messages include Claude Code attribution:
  ```
  🤖 Generated with [Claude Code](https://claude.com/claude-code)

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- [ ] **NEVER use `--no-verify`** (Git hooks are mandatory)
- [ ] CI/CD pipeline passes:
  - Fast pipeline: ktlint, Detekt, unit tests (<15 min)
  - Nightly pipeline: integration, property, fuzz, concurrency, mutation tests
- [ ] Pull Request created with comprehensive description

### 9. Sprint Status Tracking ✅

- [ ] Sprint status updated: `in-progress` → `review` → `done`
- [ ] Story marked done only after ALL DoD criteria satisfied
- [ ] Team notified of story completion (if applicable)

### 10. Definition of Done Validation ✅

- [ ] Review this entire checklist before marking story done
- [ ] If any item unchecked, story is NOT done
- [ ] Story status remains `review` until 100% DoD compliance
- [ ] Only mark `done` after validation complete

---

## Anti-Patterns to Avoid ❌

### Technical Debt Red Flags

- ❌ Skipping tests ("I'll add them later")
- ❌ Disabling quality gates temporarily
- ❌ Using `@Disabled` on failing tests
- ❌ Hardcoding values instead of configuration
- ❌ Generic exception catching (except infrastructure)
- ❌ Wildcard imports
- ❌ JUnit usage (Kotest mandatory)
- ❌ Mixing testing frameworks

### Process Violations

- ❌ Marking story done with failing tests
- ❌ Skipping code review step
- ❌ Using `git commit --no-verify` (bypasses hooks)
- ❌ Not regenerating story context for next story
- ❌ Incomplete File List or Change Log
- ❌ Missing Debug Log or Completion Notes

### Quality Shortcuts

- ❌ "It works on my machine" (use Testcontainers)
- ❌ Manual testing only (automate it)
- ❌ Copy-paste without understanding
- ❌ Ignoring architecture patterns
- ❌ Skipping documentation updates

---

## Story Workflow State Machine

```
drafted → ready-for-dev → in-progress → review → done
  ↑                                        ↓
  └────────── (changes requested) ────────┘
```

**State Transitions:**
- `drafted` → `ready-for-dev`: Story Context generated, prerequisites met
- `ready-for-dev` → `in-progress`: Developer starts implementation
- `in-progress` → `review`: All tasks complete, tests pass, DoD mostly satisfied
- `review` → `done`: Code review passed, ALL DoD criteria ✅
- `review` → `in-progress`: Changes requested, must address findings

---

## References

- [Epic 2 Retrospective](sprint-artifacts/retrospectives/epic-2-retro-2025-11-08.md) - Testing patterns
- [Epic 3 Retrospective](sprint-artifacts/retrospectives/epic-3-retro-2025-11-16.md) - Context drift finding
- [Architecture](architecture.md) - Coding standards, testing strategy
- [CONTRIBUTING.md](../CONTRIBUTING.md) - Development workflow, Git conventions

---

**Remember:** Quality over speed. A well-defined "Done" prevents rework and technical debt.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
