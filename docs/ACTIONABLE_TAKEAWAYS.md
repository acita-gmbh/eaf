# EAF Fresh Start - Actionable Takeaways

## Top 7 Things to Change (Don't Do What Old Project Did)

### 1. Story Documentation Size Limit
**Old Way**: Story 7.4 = 91KB handoff document + 5 assessment docs
**New Way**: Story = 4KB max (1KB story + 1KB risks + 1KB tests + 1KB notes)

**Action**: When you start a story, if the documentation exceeds 4KB, split the story.

**Test**: Can a developer read the entire story spec in 10 minutes? If not, too long.

---

### 2. MVP Smoke Test on Every Merge
**Old Way**: Each story tested independently, MVP validated at end (week 8)
**New Way**: Every story merge includes 20-second MVP smoke test

**Action**: Create a CI job that runs after every story merge:
```bash
# 1. Build backend (Gradle)
# 2. Build frontend (npm)
# 3. Run MVP smoke test:
#    - Create Product aggregate
#    - Query it back
#    - Update it
#    - Delete it
#    - Frontend: Load React-Admin, login, display list
# Pass all 4 → merge succeeds
# Fail any → merge blocked
```

**Why**: Story 8.3 (jOOQ migration) would have broken the MVP smoke test immediately, caught on merge day, not week 6.

---

### 3. Integration Tests Before Unit Tests
**Old Way**: Story 4.4 tested interceptor in isolation → bugs found in week 8
**New Way**: Story 4.4 has P0 integration test: "Tenant context propagates to RLS policy and query works"

**Action**: For any high-risk (score 6) story item, write the integration test FIRST:
1. Write test that validates the risk is mitigated
2. Make test fail (verify test can run)
3. Implement feature
4. Make test pass
5. Code review confirms: "This integration test proves the risk is gone"

**Example - Story 4.4 Integration Test**:
```kotlin
@SpringBootTest
fun `tenant context propagates through AspectJ interceptor to RLS policy`() {
    // Setup
    val tenantA = "tenant-a"
    createWidgetInDatabase(id = "w1", tenantId = tenantA)
    createWidgetInDatabase(id = "w2", tenantId = "tenant-b")
    
    // Action
    TenantContext.setCurrentTenantId(tenantA)
    val result = widgetQueryHandler.handle(FindWidgetsQuery())
    
    // Verify: Only tenant-a widgets returned (proves RLS works)
    assertThat(result.widgets).hasSize(1)
    assertThat(result.widgets[0].id).isEqualTo("w1")
}
```

**Why**: This test validates the entire chain works: ThreadLocal → AspectJ → DSLContext → PostgreSQL RLS

---

### 4. No Deferred Architectural Decisions
**Old Way**: Epic 2.3 uses JPA "for rapid prototyping", Story 8.3 migrates to jOOQ 6 weeks later
**New Way**: Architectural decision made in Epic 1, implemented in Epic 2, locked for entire project

**Action**: Story 1.2 (Quality Gates) must include "Architectural Compliance Check":
- Is this using the tech stack specified in docs/architecture/tech-stack.md?
- If story 2.3 uses JPA but spec says jOOQ, gate = FAIL
- No "we'll fix it later" allowed

**Rule**: If you want to use something different from architecture spec, that's an architecture change story (adds 3-5 days of work). Make that explicit.

---

### 5. CONCERNS Gates Must Have Owner + Deadline
**Old Way**: Story 7.4a gets CONCERNS gate (70/100 risk), approved anyway, never shipped
**New Way**: CONCERNS gate requires explicit accountability

**Action**: For any CONCERNS gate:
```
Risk: Dependency on Story 7.4a
Score: 6 (HIGH)
Owner: [Name]  ← Who is accountable for this?
Deadline: [Date]  ← When must this be resolved?
Acceptance: "Story 7.4a must be merged and tested before Task 3.6 of 7.4b"
Escalation: "If not met by [DATE], escalate to PM"
```

**Why**: Accountability prevents "we'll deal with it later" paralysis.

---

### 6. No Story Dependencies Longer Than 1 Story Back
**Old Way**: Story 7.4b depends on 7.4a, which depends on 7.3, 7.2, 7.1
**New Way**: Story N depends on Story N-1 only

**Action**: When you identify a story dependency:
- If dependency is >1 story back, you're building too much
- Split the stories or refactor them

**Example**:
- Bad: Story 7.4b (UI Generator) depends on Story 7.4a (Shell) depends on Story 7.3 (Aggregate Gen)
- Good: Story 7.4a (Shell) is DONE → merge
         Story 7.4b (Generator) assumes 7.4a exists → merge
         No looking back further than 7.4a

---

### 7. Visible Scope Change Log
**Old Way**: Epic 9 started as "Licensing Server MVP", became "Fix bugs from Epic 4"
**New Way**: Every story has explicit scope changes

**Action**: When story scope changes, create a "Scope Change Log":
```
Story 9.2: Implement Product Aggregate

Original Scope (2025-10-14): "Create Product aggregate with CQRS"
Revised Scope (2025-10-15): "Fix QueryHandler bug from Story 4.4"
Owner: Sarah (PO)
Reason: P0 blocker prevents Epic 9 validation
New Estimate: 1 day (instead of 3 days)
```

**Why**: When 25% of a 5-day epic becomes fixing earlier stories, that's a red flag that needs visibility.

---

## Top 3 Things to Keep (Did Well)

### 1. Security-First TDD Mindset
The old project got this right:
- P0 security tests implemented FIRST
- Then implementation
- Then integration testing

Keep this. Just make sure P0 tests are integration tests, not unit tests.

### 2. Architecture Style (Hexagonal/CQRS/ES)
The pattern was fundamentally sound:
- Clear module boundaries
- Event sourcing for audit
- Separate read/write models

Don't second-guess this. It's the right choice.

### 3. Quality Gates Infrastructure
ktlint, Detekt, test coverage, mutation testing were all appropriate.

Don't water this down. But make it fast (pre-commit in <30 seconds).

---

## The Single Most Important Rule

**Every story merge must answer: "Does the MVP still work?"**

If the answer is "no", the story doesn't merge. Period.

This single rule would have caught:
- Story 8.3 (jOOQ migration) breaking the MVP
- Story 9.2 (QueryHandler bug) on merge day, not week 8
- Story 9.3 (AspectJ pointcut) immediately when introduced

---

## Decision Framework for New Project

When you're about to make a decision, ask:

| Decision | Old Way (Failed) | New Way (Correct) |
|----------|-----------------|------------------|
| Story size | 90KB, 3 weeks work | 4KB, 2 weeks max |
| Risk acceptance | Approve CONCERNS, hope it works | Owner + deadline + actionable items |
| MVP validation | Week 8 | Week 1, 2, 3 (every merge) |
| Architectural decision | Use JPA now, migrate later | jOOQ from day 1 (no migration) |
| Test strategy | Unit tests of components | Integration test of full chain |
| Dependency | Story N-5 → Story N | Story N-1 → Story N only |
| Documentation | Detailed specs | Just what you need to build |

---

## Success Metrics for New Project

**Old Project**:
- Attempt: 49 stories
- Complete: ~30-35 stories
- MVP Status: Broken (week 8)
- Timeline: 8+ weeks

**New Project Success Criteria**:
- Week 1: Foundation + walking skeleton (1 aggregate)
- Week 2: Integration test: Full CRUD works
- Week 3: MVP validation passes (create → read → update → delete)
- Week 4-5: Scale to 2-3 aggregates
- Total: 5-6 weeks, no rework

**Kill Criteria** (stop and re-plan if):
- Week 2: MVP doesn't have working create/read
- Week 3: Integration bugs discovered that take >1 day to fix
- Stories exceeding 2 weeks
- CONCERNS gates appearing in critical path

---

## TL;DR - Just Read This Section

1. **Stories**: 4KB docs max, 2 weeks work max
2. **Testing**: Integration tests first (P0), unit tests second
3. **MVP**: Validate on every merge, not at the end
4. **Scope**: No deferred architectural decisions
5. **Risk**: CONCERNS gates need owner + deadline
6. **Dependencies**: Only 1 story back
7. **Changes**: Maintain visible scope change log

Follow these 7 rules, and you won't repeat the old project's mistakes.

---

Generated: 2025-10-30
Based on comprehensive analysis of failed old project (49 stories, 170+ assessment docs, 8 weeks)
