# Mutant-Kraken Integration Research

**Date:** 2025-12-01
**Status:** Research Complete
**Recommendation:** Not production-ready; experimental evaluation possible

## Executive Summary

[Mutant-Kraken](https://github.com/JosueMolinaMorales/mutant-kraken) is a Rust-based, source-level mutation testing tool specifically designed for Kotlin. While it offers Kotlin-native mutation operators that address limitations in bytecode-based tools like Pitest, **it is not ready for production use** in this project due to:

1. **Beta status** - Explicitly stated as "not ready for production use"
2. **No Gradle plugin** - CLI-only, no native multi-module support
3. **Limited multi-module handling** - Runs tests at project root only
4. **No Spring Boot awareness** - May cause issues with test context loading

## How Mutant-Kraken Works

### Architecture

Unlike Pitest (bytecode mutation), Mutant-Kraken operates at the **source code level**:

1. **File Discovery** - Scans for `.kt` files, respecting ignore patterns
2. **AST Parsing** - Uses [Tree-sitter](https://tree-sitter.github.io/) to parse Kotlin into an AST
3. **Mutation Generation** - Creates mutated source files in `mutant-kraken-dist/mutations/`
4. **Test Execution** - Runs `./gradlew clean assemble test` per mutation

### Mutation Operators (15 Total)

| Category | Operators | Description |
|----------|-----------|-------------|
| **Standard** | ArithmeticReplacement, LogicalReplacement, RelationalReplacement, AssignmentReplacement, UnaryRemoval, UnaryReplacement, LiteralChange, ExceptionChange | Traditional mutation operators |
| **Kotlin-Specific** | NotNullAssertion, ElvisRemove, ElvisLiteralChange, WhenRemoveBranch, RemoveLabel, FunctionalBinaryReplacement, FunctionalReplacement | Target `!!`, `?:`, `when`, labels, `any()`/`all()`/`none()` |

### Key Advantage: Kotlin-Native Operators

Mutant-Kraken can mutate Kotlin-specific constructs that Pitest struggles with:

```kotlin
// ElvisRemove: Removes elvis operator
val name = user?.name ?: "Unknown"  // mutates to: user?.name

// NotNullAssertion: Mutates !! operator
val name = user!!.name  // mutates to: user?.name
val name = user!!.name  // mutates to: user.name

// FunctionalReplacement: Swaps functional methods
list.any { it > 0 }  // mutates to: list.all { it > 0 }
list.first()         // mutates to: list.last()

// WhenRemoveBranch: Removes when branches
when (status) {
    PENDING -> handlePending()
    APPROVED -> handleApproved()  // mutates by removing this branch
    REJECTED -> handleRejected()
}
```

## Integration Challenges for EAF Monorepo

### 1. Multi-Module Structure Incompatibility

EAF has 11 modules across two component groups. Mutant-Kraken expects:
- Single `gradlew` at project root ✅ (we have this)
- Tests run via `./gradlew test` with file-based filtering

**Problem:** When mutating a file in `eaf-core`, running `./gradlew test` will execute ALL tests across ALL modules, including dvmm modules that may have complex Spring Boot test contexts.

```text
eaf/
├── eaf-core/            # If file here is mutated...
├── eaf-eventsourcing/
├── ...
└── dvmm/
    └── dvmm-app/        # ...this module's integration tests still run
```

### 2. No Module-Specific Test Filtering

Mutant-Kraken builds test commands like:
```bash
./gradlew test --parallel --quiet --tests "*FileName*"
```

This is suboptimal because:
- It matches by filename across ALL modules
- No way to specify `:eaf:eaf-core:test` for a mutation in eaf-core
- Integration tests in `dvmm-app` may timeout or fail due to Spring context

### 3. Test Timeout Defaults

The default 120-second timeout may be insufficient for:
- Integration tests with Testcontainers (PostgreSQL, Keycloak)
- Spring Boot test context initialization
- First-run compilation

### 4. No Kover/Pitest Coexistence Strategy

Running both tools would require:
- Separate CI jobs
- Different coverage thresholds (current: 70% Pitest)
- Potentially conflicting reports

## Arcmutate Integration (Implemented)

The project now uses **Arcmutate** (commercial Pitest plugins) for production-ready Kotlin and Spring mutation testing.

### Configuration

**Gradle Plugin** (`build-logic/conventions/src/main/kotlin/eaf.pitest-conventions.gradle.kts`):
- `pitest-kotlin-plugin:1.5.0` - Kotlin bytecode understanding
- `arcmutate-spring:1.1.2` - Spring annotation mutations

**Features Enabled:**
- `KOTLIN` - Full Kotlin support
- `KOTLIN_NO_NULLS` - Filters null-check intrinsics
- `KOTLIN_EXTRA` - Additional junk mutation filters
- `KOTLIN_RETURNS`, `KOTLIN_REMOVE_DISTINCT`, `KOTLIN_REMOVE_SORTED` - Kotlin-specific mutators
- `SPRING` - Spring annotation mutations (validation, security, response)

### License Management

**For Local Development:**
1. Obtain `arcmutate-licence.txt` from team lead
2. Place at project root (gitignored)

**For CI (GitHub Actions):**
1. Add license content to GitHub Secret: `ARCMUTATE_LICENSE`
2. CI workflow automatically injects via environment variable
3. Pitest task writes license file before execution

```bash
# GitHub Secret setup (repo admin only)
# Settings → Secrets and variables → Actions → New repository secret
# Name: ARCMUTATE_LICENSE
# Value: <paste full license file content>
```

### Running Locally

```bash
# Ensure license file exists
ls arcmutate-licence.txt

# Run mutation testing
./gradlew pitest
```

## Pitest + Arcmutate vs Mutant-Kraken Comparison

| Aspect | Pitest + Arcmutate (Current) | Mutant-Kraken |
|--------|------------------------------|---------------|
| **Integration** | ✅ Gradle plugin | CLI only |
| **Mutation Level** | JVM bytecode | Source code |
| **Multi-module** | ✅ Per-module execution | ❌ Root-level only |
| **Kotlin Support** | ✅ Full (Arcmutate) | Native operators |
| **Inline Functions** | ✅ Bytecode rewriting | Theoretically supported |
| **Coroutines** | ✅ Filtered (Arcmutate) | Unknown |
| **Spring Boot** | ✅ Spring plugin | ❌ No awareness |
| **CI Integration** | ✅ Gradle task + secrets | ❌ Manual CLI |
| **Maturity** | Production | Beta |
| **License** | Commercial (Arcmutate) | Open source |

## Experimental Integration Approach

If you want to evaluate Mutant-Kraken experimentally, here's how:

### 1. Installation

```bash
# Option A: Homebrew (macOS)
brew tap JosueMolinaMorales/mutant-kraken
brew install mutant-kraken

# Option B: Cargo (cross-platform)
cargo install mutant-kraken
```

### 2. Configuration File

Create `mutantkraken.config.json` at project root:

```json
{
  "general": {
    "timeout": 300,
    "operators": [
      "NotNullAssertionOperator",
      "ElvisRemoveOperator",
      "ElvisLiteralChangeOperator",
      "WhenRemoveBranchOperator",
      "FunctionalReplacementOperator",
      "FunctionalBinaryReplacementOperator",
      "ArithmeticReplacementOperator",
      "LogicalReplacementOperator",
      "RelationalReplacementOperator"
    ]
  },
  "ignore": {
    "ignore_files": [
      "^.*Test\\.kt$",
      "^.*IntegrationTest\\.kt$",
      "^.*Spec\\.kt$"
    ],
    "ignore_directories": [
      "build",
      "bin",
      ".gradle",
      ".idea",
      "gradle",
      "dvmm-web",
      "dvmm-infrastructure",
      "dvmm-app"
    ]
  },
  "threading": {
    "max_threads": 4
  },
  "output": {
    "display_end_table": true
  },
  "logging": {
    "log_level": "INFO"
  }
}
```

### 3. Single-Module Evaluation (Workaround)

Since multi-module support is lacking, evaluate on a single pure-Kotlin module:

```bash
# Option A: Run on eaf-core only
cd eaf/eaf-core
mutant-kraken mutate src/main/kotlin

# Option B: Run from root with aggressive ignores
# (configure ignore_directories to exclude all but one module)
mutant-kraken mutate .
```

### 4. Add to .gitignore

```text
# Mutant-Kraken output
mutant-kraken-dist/
```

## Recommendations

### Short-Term (Now)

**Continue with Pitest** for production mutation testing:
- Proven stability
- Gradle integration
- CI/CD support
- 70% threshold already working

### Medium-Term (Experimental)

**Evaluate Mutant-Kraken on `eaf-core` only:**
- Pure Kotlin module, no Spring dependencies
- Domain primitives ideal for mutation testing
- Low risk, contained scope

```bash
# Single experimental run
cd eaf/eaf-core
mutant-kraken mutate src/main/kotlin/de/acci/eaf/core
```

Compare results with Pitest to identify:
- Kotlin-specific mutations Pitest misses
- False positives/negatives in each tool
- Performance characteristics

### Long-Term (Watch)

**Monitor Issue #37** (Gradle plugin request) on GitHub:
- Native Gradle plugin would solve multi-module issues
- Would enable per-module execution
- Would integrate with existing build pipeline

**Consider Arcmutate** if budget allows:
- Commercial Pitest extension
- Deep Kotlin bytecode understanding
- Filters junk mutations from compiler-generated code
- Supports inline functions, coroutines, sealed classes

## Integration Effort Estimate

| Approach | Effort | Risk | Value |
|----------|--------|------|-------|
| Experimental eaf-core only | Low (2-4 hours) | Low | Medium - validates tool capability |
| Full monorepo integration | High (2-3 days) | High | Low - tool not ready |
| Wait for Gradle plugin | None | None | High - proper integration later |
| Arcmutate commercial | Medium (1 day) | Low | High - production-ready Kotlin support |

## Experimental Setup (Implemented)

The following experimental setup has been added to the project for evaluating Mutant-Kraken on EAF core modules.

### Files Added

| File | Purpose |
|------|---------|
| `mutantkraken.config.json` | Root configuration for experimental runs |
| `scripts/run-mutant-kraken.sh` | Wrapper script for per-module execution |

### Target Modules

**Pure Kotlin (Recommended for testing):**
- `eaf-core` - Domain primitives (Entity, AggregateRoot, ValueObject)
- `eaf-auth` - IdP-agnostic authentication interfaces
- `eaf-tenant` - Multi-tenancy primitives

**Spring Modules (Higher risk of timeouts):**
- `eaf-eventsourcing` - Event Store with PostgreSQL
- `eaf-auth-keycloak` - Keycloak integration

### Running the Experiment

```bash
# 1. Install mutant-kraken
cargo install mutant-kraken

# 2. Run on all pure Kotlin modules
./scripts/run-mutant-kraken.sh --all

# 3. Run on specific module
./scripts/run-mutant-kraken.sh eaf-core

# 4. View results
open mutant-kraken-results/eaf-core/index.html
```

### Expected Output

Results are collected in `mutant-kraken-results/<module>/`:
- `index.html` - Interactive mutation report
- `mutations.json` - Raw mutation data
- Individual mutant files for inspection

### Configuration Details

The root `mutantkraken.config.json` excludes:
- All DVMM product modules (focus on framework only)
- Test files (`*Test.kt`, `*IntegrationTest.kt`)
- Build directories

Timeout is set to 300s to accommodate first-run compilation.

## Conclusion

Mutant-Kraken represents a promising future for Kotlin-native mutation testing with its source-level approach and Kotlin-specific operators. However, for the EAF monorepo:

1. **Not production-ready** - Beta status, no Gradle plugin
2. **Multi-module issues** - Would require workarounds that defeat the purpose
3. **Pitest sufficient** - Current 70% threshold provides adequate mutation coverage

**Recommended action:** Run a single experimental evaluation on `eaf-core` to assess the tool's potential, but continue relying on Pitest for CI quality gates.

## Sources

- [Mutant-Kraken GitHub Repository](https://github.com/JosueMolinaMorales/mutant-kraken)
- [ICST 2024 Mutation Workshop Paper](https://conf.researchr.org/details/icst-2024/mutation-2024-papers/2/Mutant-Kraken-A-Mutation-Testing-Tool-for-Kotlin)
- [IEEE Xplore Publication](https://ieeexplore.ieee.org/document/10675826/)
- [Gradle Plugin Feature Request (Issue #37)](https://github.com/JosueMolinaMorales/mutant-kraken/issues/37)
- [Crates.io Package](https://crates.io/crates/mutant-kraken)
