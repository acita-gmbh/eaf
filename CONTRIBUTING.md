# Contributing to EAF

**Enterprise Application Framework (v0.1)** - Contribution Guide

---

## Pre-Commit Hooks (Story 8.2)

### Overview

Pre-commit hooks automatically validate code quality **before** commits (<30s target), catching issues locally instead of in CI (15min wait).

### What Runs Pre-Commit?

**Current Implementation** (Tasks 1-4):
1. ✅ **Format validation** (ktlint) - Staged files only
2. ✅ **Test naming** (Konsist) - Story 8.1 standard enforcement
3. ⏳ **Static analysis** (Detekt) - Fast profile (Task 2.3 pending)
4. ⏳ **Unit tests** (smart) - Changed modules only (Task 3 pending integration)
5. ✅ **Commit message** - [JIRA-XXX] type: description format

**Target**: <30 seconds total execution time

### Installation

**Automatic** (recommended):
```bash
./scripts/init-dev.sh
# Hooks install during onboarding
```

**Manual**:
```bash
./gradlew installGitHooks
```

### Commit Message Format

**Required Pattern**:
```
[JIRA-XXX] type: description
[Epic X] type: description
```

**Types**: feat, fix, docs, style, refactor, test, chore

**Examples**:
```bash
git commit -m "[DPCMSG-1234] feat: add tenant isolation to Widget"
git commit -m "[Epic 8] fix: resolve test naming violations"
git commit -m "[DPCMSG-5678] docs: update API documentation"
```

### Bypassing Hooks (Emergency Only)

**When to bypass**:
- Emergency production hotfix
- WIP commits for collaboration
- Merge conflict resolution
- Generated code commits

**How to bypass**:
```bash
git commit --no-verify -m "..."
# Or short form:
git commit -n -m "..."
```

**WIP commits** auto-bypass:
```bash
git commit -m "WIP: experimenting with feature"
# Validation skipped automatically
```

### Troubleshooting

**Hook not running?**
```bash
# Reinstall
./gradlew installGitHooks

# Verify installation
ls -la .git/hooks/pre-commit
```

**Hook too slow?**
- Check metrics: `cat .git/hooks/metrics.log`
- Report if p95 >30s

**Hook blocks valid commit?**
- Use `--no-verify` temporarily
- Report issue to team for rule adjustment

**Uninstall hooks**:
```bash
./gradlew uninstallGitHooks
```

---

## Development Workflow

### Quality Gates

**Local** (pre-commit):
- Format, test naming, commit message (<30s)

**CI** (full validation):
- All quality gates (ktlint, Detekt, Konsist, tests, coverage)
- Integration tests with Testcontainers
- Mutation testing (Pitest)

### Test Naming Standard (Story 8.1)

**Format**: `{EPIC}.{STORY}-{TYPE}-{SEQ}: {Description}`

**Example**:
```kotlin
test("8.2-UNIT-001: installGitHooks creates hook files") {
    // Test logic
}
```

See: docs/architecture/test-strategy-and-standards-revision-3.md

---

## Code Standards

- **NO wildcard imports** - Explicit only
- **Kotest ONLY** - JUnit forbidden
- **Version Catalog** - All deps from gradle/libs.versions.toml
- **Zero violations** - ktlint, Detekt, Konsist must pass

See: docs/architecture/coding-standards-revision-2.md

---

## Getting Help

- Architecture questions: docs/architecture/
- Development workflow: docs/architecture/development-workflow.md
- Pre-commit hooks: docs/development/pre-commit-hooks-guide.md

---

**Version**: 0.1.0 (Story 8.2 - Pre-Commit Hooks)
**Last Updated**: 2025-10-05
