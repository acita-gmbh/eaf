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
