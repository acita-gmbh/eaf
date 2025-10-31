# Story 1.10: Git Hooks for Quality Gates

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR008 (Quality Gates), FR025 (Local Dev Workflow)

---

## User Story

As a framework developer,
I want Git hooks that enforce quality gates before commits and pushes,
So that code quality issues are caught locally before CI/CD.

---

## Acceptance Criteria

1. ✅ .git-hooks/pre-commit script runs ktlint check (<5s)
2. ✅ .git-hooks/pre-push script runs Detekt + fast unit tests (<30s)
3. ✅ scripts/install-git-hooks.sh installs hooks (called by init-dev.sh)
4. ✅ Hooks can be bypassed with --no-verify flag (but discouraged)
5. ✅ .github/workflows/validate-hooks.yml ensures hooks match CI requirements
6. ✅ Clear error messages when hooks fail with remediation instructions
7. ✅ Hooks tested with intentional violations (formatting error, failing test)

---

## Prerequisites

**Story 1.8** - Spring Modulith Boundary Enforcement (quality gates exist)

---

## Technical Notes

### Pre-Commit Hook (Fast - <5s)

**.git-hooks/pre-commit:**
```bash
#!/bin/bash
set -e

echo "Running pre-commit checks..."

# Run ktlint formatting check
echo "▶ ktlint formatting check..."
./gradlew ktlintCheck --quiet || {
    echo ""
    echo "❌ ktlint formatting violations detected!"
    echo ""
    echo "Fix with: ./gradlew ktlintFormat"
    echo "Or bypass (discouraged): git commit --no-verify"
    exit 1
}

echo "✅ Pre-commit checks passed"
```

### Pre-Push Hook (Standard - <30s)

**.git-hooks/pre-push:**
```bash
#!/bin/bash
set -e

echo "Running pre-push checks..."

# Run Detekt static analysis
echo "▶ Detekt static analysis..."
./gradlew detekt --quiet || {
    echo ""
    echo "❌ Detekt violations detected!"
    echo ""
    echo "Review report: build/reports/detekt/detekt.html"
    echo "Or bypass (discouraged): git push --no-verify"
    exit 1
}

# Run fast unit tests
echo "▶ Fast unit tests..."
./gradlew test --quiet || {
    echo ""
    echo "❌ Unit tests failed!"
    echo ""
    echo "Fix tests before pushing"
    echo "Or bypass (discouraged): git push --no-verify"
    exit 1
}

echo "✅ Pre-push checks passed"
```

### Hook Installation Script

**scripts/install-git-hooks.sh:**
```bash
#!/bin/bash
set -e

echo "Installing Git hooks..."

# Copy hooks to .git/hooks/
cp .git-hooks/pre-commit .git/hooks/pre-commit
cp .git-hooks/pre-push .git/hooks/pre-push

# Make hooks executable
chmod +x .git/hooks/pre-commit
chmod +x .git/hooks/pre-push

echo "✅ Git hooks installed"
echo ""
echo "Pre-commit: ktlint formatting (<5s)"
echo "Pre-push: Detekt + unit tests (<30s)"
echo ""
echo "Bypass with --no-verify (discouraged)"
```

### Hook Validation Pipeline

**.github/workflows/validate-hooks.yml:**
```yaml
name: Validate Git Hooks

on:
  push:
    paths:
      - '.git-hooks/**'
      - '.github/workflows/ci.yml'

jobs:
  validate-hooks:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Verify hooks match CI requirements
        run: |
          # Ensure pre-commit runs ktlint
          grep -q "ktlintCheck" .git-hooks/pre-commit
          # Ensure pre-push runs Detekt + tests
          grep -q "detekt" .git-hooks/pre-push
          grep -q "test" .git-hooks/pre-push
```

---

## Implementation Checklist

- [ ] Create .git-hooks/ directory
- [ ] Implement pre-commit hook (ktlint)
- [ ] Implement pre-push hook (Detekt + unit tests)
- [ ] Update scripts/install-git-hooks.sh
- [ ] Make hooks executable (chmod +x)
- [ ] Create .github/workflows/validate-hooks.yml
- [ ] Test pre-commit with formatting violation
- [ ] Test pre-push with Detekt violation
- [ ] Test pre-push with failing test
- [ ] Verify hooks can be bypassed with --no-verify
- [ ] Document in CONTRIBUTING.md
- [ ] Commit: "Add Git hooks for local quality gate enforcement"

---

## Test Evidence

- [ ] Pre-commit hook runs on `git commit`
- [ ] Pre-commit executes in <5 seconds
- [ ] Pre-commit fails on ktlint violations
- [ ] Pre-push hook runs on `git push`
- [ ] Pre-push executes in <30 seconds
- [ ] Pre-push fails on Detekt violations
- [ ] Pre-push fails on test failures
- [ ] `--no-verify` bypasses hooks
- [ ] Clear error messages with remediation

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Both hooks tested with violations
- [ ] Hooks installed by init-dev.sh
- [ ] Execution times meet targets (<5s, <30s)
- [ ] validate-hooks.yml pipeline passes
- [ ] Documentation in CONTRIBUTING.md
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 1.9 - CI/CD Pipeline Foundation
**Next Story:** Story 1.11 - Foundation Documentation

---

## References

- PRD: FR008, FR025
- Architecture: Section 19 (Development Workflow)
- Tech Spec: Section 3 (FR008, FR025 Implementation)
