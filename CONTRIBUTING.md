# Contributing to EAF v1.0

Thank you for your interest in contributing to the Enterprise Application Framework! This guide will help you understand our development workflow and quality standards.

---

## Development Setup

### 1. One-Command Initialization

The fastest way to get started:

```bash
./scripts/init-dev.sh
```

This script will:
- Start all Docker services (PostgreSQL, Keycloak, Redis, Prometheus, Grafana)
- Install Git hooks for quality enforcement
- Download project dependencies
- Verify environment health

**Execution time:** ~22 seconds

### 2. Verify Setup

```bash
./gradlew build
```

If this completes successfully, your environment is ready for development!

---

## Development Workflow

### Test-Driven Development (Mandatory)

EAF follows **Constitutional TDD** (Red-Green-Refactor cycle). This is non-negotiable and enforced by Git hooks.

**The TDD Cycle:**

1. **RED:** Write a failing test first
   ```kotlin
   test("should create widget with valid name") {
       // Test implementation (fails initially)
   }
   ```

2. **GREEN:** Write minimal code to make the test pass
   ```kotlin
   fun createWidget(name: String): Widget {
       return Widget(name)
   }
   ```

3. **REFACTOR:** Improve code quality while keeping tests green
   ```kotlin
   fun createWidget(name: String): Either<DomainError, Widget> {
       return Widget.create(name).bind()
   }
   ```

**Why TDD?**
- Catches bugs early (before they reach production)
- Ensures code is testable by design
- Provides living documentation of expected behavior
- Reduces debugging time significantly

**Enforcement:** Git hooks will reject commits that add production code without corresponding tests.

---

### Quality Gates

EAF enforces quality at multiple stages to catch issues as early as possible:

#### Pre-Commit Hooks (<5s)

Runs automatically when you commit:

- **ktlint:** Code formatting check
- **Commit message validation:** Ensures proper format

```bash
# Typical commit flow
git add .
git commit -m "[DPCMSG-1234] feat: add widget aggregate"
# Hooks run automatically
```

#### Pre-Push Hooks (<30s)

Runs automatically when you push:

- **Detekt:** Static analysis for code quality
- **Fast unit tests:** Quick validation of business logic

```bash
git push origin feature/my-feature
# Pre-push hooks validate before pushing
```

#### CI/CD Pipeline (<15min)

Runs on all pull requests and commits to main:

- Full build
- All unit tests
- Integration tests with Testcontainers
- Architecture tests (Konsist validates module boundaries)
- Code coverage check (Kover, 85%+ target)

#### Nightly Pipeline (~2.5h)

Deep validation runs every night:

- Property-based tests (Kotest)
- Fuzz testing (Jazzer)
- Concurrency tests (LitmusKt)
- Mutation testing (Pitest, 60-70% target)

---

### Continuous Story Context (CRITICAL)

**Epic 3 Lesson Learned:** Story Context XML becomes stale after 6-7 stories, causing significant rework and investigation time (Epic 3 Stories 3.7-3.10 experienced 9.5h+ of unnecessary debugging due to outdated context).

#### The Problem

Story Context XML generated at epic start contains:
- Architectural patterns and decisions from initial stories
- Test configurations and infrastructure patterns
- Security implementations and validation approaches

As an epic progresses (Stories 3.4, 3.5, 3.6...), new patterns emerge:
- Nullable Design Pattern adoption
- Test configuration improvements (@ServiceConnection timing)
- Security validation refinements

But Story Context for later stories (3.7-3.10) still references **outdated patterns from Stories 3.1-3.3**, missing critical learnings from 3.4-3.6.

**Impact:**
- Story 3.7: Multiple test configuration iterations (should've used Nullable Pattern)
- Story 3.10: 9.5 hours investigating patterns that were already solved in Story 3.1-3.3 but not in context

#### The Solution: Continuous Regeneration

**MANDATORY PROCESS - After completing Story X.Y:**

1. **Mark Story Complete:**
   ```bash
   # Story X.Y implementation done
   git push origin story/X-Y-feature-name
   # PR merged, story marked 'done'
   ```

2. **Immediately Regenerate Context for Next Story:**
   ```bash
   # Switch to SM Agent or use workflow directly
   /bmad:bmm:workflows:story-context

   # Select Story X.(Y+1) from drafted stories
   # Workflow generates fresh context with:
   # - Patterns from ALL completed stories (X.1 through X.Y)
   # - Latest test configurations
   # - Updated architecture decisions
   # - Fresh security patterns
   ```

3. **Verify Fresh Context Includes Recent Learnings:**
   - Check context includes patterns from just-completed Story X.Y
   - Verify code artifacts reference latest implementations
   - Confirm test patterns reflect current best practices

4. **Continue with Next Story:**
   ```bash
   # Story X.(Y+1) now has comprehensive, up-to-date context
   /bmad:bmm:agents:dev
   # Start implementation with full context
   ```

#### Why This Matters

**Without Continuous Context:**
- Context drift accumulates exponentially (each story further behind)
- Developers repeat solved problems (wasted 6-12h per story in Epic 3)
- Quality inconsistencies (older patterns vs newer patterns)
- Frustration and confusion ("why doesn't this work like Story 3.4?")

**With Continuous Context:**
- Always working with latest patterns and decisions
- No context drift or stale references
- Consistent quality across all stories in epic
- Faster implementation (patterns readily available)

#### Automation Options

**Option 1: Manual Workflow (Current)**
- After Story X.Y done → manually run `story-context` for X.(Y+1)
- Scrum Master reminder in retrospective
- Tracked in Story Definition of Done checklist

**Option 2: Git Hook (Future Enhancement)**
```bash
# Post-merge hook on main branch
#!/bin/bash
if [[ "$BRANCH" == story/* ]]; then
    echo "Story merged! Remember to regenerate context for next story."
    echo "Run: /bmad:bmm:workflows:story-context"
fi
```

**Option 3: CI/CD Integration (Future Enhancement)**
- Detect story merge to main
- Auto-generate PR with refreshed context for next story
- Human review and merge

#### Story DoD Integration

This continuous context process is now **MANDATORY** in Story Definition of Done:

> **6. Story Context Maintenance ✅**
>
> - [ ] **After completing Story X.Y → regenerate context for Story X.(Y+1)**
> - [ ] Run `story-context` workflow for next story in epic
> - [ ] Verify context includes learnings from completed story
> - [ ] Prevents context drift (Epic 3 Stories 3.7-3.10 issue)

See: [docs/story-definition-of-done.md](docs/story-definition-of-done.md)

#### Example: Epic 3 Timeline

**What Happened (Without Continuous Context):**
```
Story 3.1-3.3: Context generated at epic start
Story 3.4-3.6: New patterns emerge (Nullable, @ServiceConnection)
Story 3.7-3.10: Still using STALE context from 3.1-3.3
Result: 9.5h wasted on Story 3.10, multiple iterations on 3.7
```

**What Should Have Happened (With Continuous Context):**
```
Story 3.1: Context generated
Story 3.2: Context regenerated (includes 3.1 learnings)
Story 3.3: Context regenerated (includes 3.1-3.2 learnings)
Story 3.4: Context regenerated (includes 3.1-3.3 learnings)
...
Story 3.10: Context regenerated (includes 3.1-3.9 learnings)
Result: Smooth implementation, no unnecessary debugging
```

#### Key Takeaway

**Continuous Story Context is not optional** - it's a critical quality practice that prevents exponential time waste across epics. The 15-20 minutes spent regenerating context after each story saves 6-12 hours of debugging and rework on subsequent stories.

**Remember:** Fresh context = Fresh implementation = Quality outcomes.

---

### Code Standards

#### Mandatory Rules (Zero-Tolerance)

1. **NO wildcard imports**
   ```kotlin
   // ✅ CORRECT
   import com.axians.eaf.framework.core.domain.AggregateRoot
   import arrow.core.Either

   // ❌ FORBIDDEN
   import com.axians.eaf.framework.core.domain.*
   import arrow.core.*
   ```

2. **Kotest ONLY** - JUnit is explicitly forbidden
   ```kotlin
   // ✅ CORRECT
   class WidgetTest : FunSpec({
       test("should create widget") { }
   })

   // ❌ FORBIDDEN
   @Test
   fun shouldCreateWidget() { }
   ```

3. **Version Catalog Required** - All versions in `gradle/libs.versions.toml`
   ```kotlin
   // ✅ CORRECT
   dependencies {
       implementation(libs.spring.boot.starter.web)
   }

   // ❌ FORBIDDEN
   dependencies {
       implementation("org.springframework.boot:spring-boot-starter-web:3.5.7")
   }
   ```

4. **Zero violations** - ktlint, Detekt, and Konsist must pass without warnings

#### Coverage Targets

- **Line Coverage:** 85%+ (Kover)
- **Mutation Score:** 60-70% (Pitest)
- **Architecture:** All module boundaries validated (Konsist)

#### Kotlin Style

- **Indentation:** 4 spaces
- **Max line length:** 120 characters
- **Formatting:** ktlint (official Kotlin style guide)

For complete coding standards, see: [docs/architecture/coding-standards.md](docs/architecture/coding-standards.md)

---

## Pull Request Process

### 1. Fork and Branch

```bash
# Fork the repository on GitHub
git clone <your-fork-url>
cd eaf-v1

# Create feature branch
git checkout -b feature/my-feature
```

### 2. Develop with TDD

```bash
# RED: Write failing test
# GREEN: Make it pass
# REFACTOR: Improve code

# Run tests frequently
./gradlew test
```

### 3. Ensure Quality Gates Pass

Before pushing, ensure all quality gates pass locally:

```bash
# Run all checks
./gradlew check

# Format code automatically
./gradlew ktlintFormat

# Run static analysis
./gradlew detekt
```

### 4. Commit Changes

```bash
# Hooks run automatically
git commit -m "[DPCMSG-1234] feat: add new aggregate"
```

**Commit Message Format:**
- Prefix: `[JIRA-XXX]` or `[Epic X]`
- Type: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- Description: Clear, concise summary of changes

### 5. Push and Create PR

```bash
# Push to your fork
git push origin feature/my-feature
```

Create a pull request on GitHub with:
- **Title:** Clear summary of changes
- **Description:** Explain what, why, and how
- **Linked Issues:** Reference related Jira tickets or GitHub issues
- **Test Evidence:** Describe how you tested the changes

### 6. Address Review Feedback

- Respond to all review comments
- Make requested changes
- Push updates to the same branch
- Request re-review when ready

### 7. Merge

Once approved:
- Squash commits if requested
- Ensure CI/CD passes
- Merge via GitHub UI

---

## Testing Requirements

### Test Framework

- **Primary:** Kotest 6.0.4
- **Integration:** Testcontainers for real dependencies
- **Never mock:** Business logic (use Nullable Pattern instead)

### Test Types

1. **Unit Tests:** Fast business logic tests (<30s total)
2. **Integration Tests:** Real dependencies via Testcontainers (<3min)
3. **Property Tests:** Invariant validation (nightly)
4. **Fuzz Tests:** Security vulnerability detection (nightly)
5. **Concurrency Tests:** Race condition detection (nightly)
6. **Mutation Tests:** Test effectiveness validation (nightly)

### Spring Boot Integration Test Pattern

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class WidgetIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("should create widget via REST API") {
            mockMvc.perform(post("/api/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Test Widget"}"""))
                .andExpect(status().isCreated())
        }
    }
}
```

**Critical:** Use `@Autowired` field injection + `init` block (NOT constructor injection)

---

## Getting Help

### Documentation

- **Getting Started:** [docs/getting-started/00-prerequisites.md](docs/getting-started/00-prerequisites.md)
- **Architecture:** [docs/architecture.md](docs/architecture.md)
- **PRD:** [docs/PRD.md](docs/PRD.md)
- **Tech Spec:** [docs/tech-spec.md](docs/tech-spec.md)

### Communication Channels

- **Slack:** #eaf-development
- **Email:** eaf-team@axians.com
- **Issues:** GitHub Issues for bug reports and feature requests

### Common Questions

**Q: Why is JUnit forbidden?**
A: Kotest provides better Kotlin integration, more expressive DSL, and native support for property-based testing.

**Q: Can I use MockK for mocking?**
A: Only for infrastructure. Never mock business logic - use the Nullable Design Pattern instead.

**Q: How do I run just one test?**
```bash
./gradlew test --tests "WidgetTest"
```

**Q: How do I skip hooks temporarily?**
```bash
git commit --no-verify -m "WIP: work in progress"
```

**Q: Where do I put new features?**
- Framework modules: `framework/` (libraries only)
- Product modules: `products/` (applications with domain logic)
- See [Architecture Guide](docs/architecture.md) for details

---

## Code of Conduct

- Be respectful and professional
- Provide constructive feedback
- Help others learn and grow
- Follow architectural decisions (see ADRs)
- Maintain code quality standards

---

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

---

**Thank you for contributing to EAF!** 🎉
