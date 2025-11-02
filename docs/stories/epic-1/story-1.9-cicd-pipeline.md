# Story 1.9: CI/CD Pipeline Foundation

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR008 (Quality Gates), NFR002 (Security - OWASP scanning)

---

## User Story

As a framework developer,
I want GitHub Actions CI/CD pipelines for automated quality validation,
So that all commits and PRs are automatically tested.

---

## Acceptance Criteria

1. ✅ .github/workflows/ci.yml created (fast feedback pipeline <15min)
2. ✅ CI pipeline runs: build, ktlint, Detekt, unit tests, integration tests
3. ✅ .github/workflows/nightly.yml created (deep validation ~2.5h)
4. ✅ Nightly pipeline runs: property tests, fuzz tests, mutation tests, concurrency tests
5. ✅ .github/workflows/security-review.yml with OWASP dependency check
6. ✅ Pipeline runs on: push to main, pull requests, nightly schedule
7. ✅ All pipelines pass with current codebase
8. ✅ Pipeline results visible in GitHub Actions

---

## Prerequisites

**Story 1.8** - Spring Modulith Boundary Enforcement (architecture tests)

---

## Technical Notes

**📝 Note from Story 1.6 Code Review (2025-11-02):**
Consider adding shellcheck static analysis for shell script quality enforcement. EAF now includes 4 bash scripts (init-dev.sh, health-check.sh, seed-data.sh, install-git-hooks.sh) that should be validated in CI/CD pipeline.

**Recommended addition to ci.yml:**
```yaml
- name: Shell Script Analysis (shellcheck)
  run: |
    # Install shellcheck
    sudo apt-get update && sudo apt-get install -y shellcheck
    # Run shellcheck on all bash scripts
    find scripts/ -name "*.sh" -exec shellcheck {} \;
```

### CI Pipeline (Fast Feedback - <15min)

**.github/workflows/ci.yml:**
```yaml
name: CI - Fast Feedback

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build
        run: ./gradlew build --no-daemon

      - name: ktlint Check
        run: ./gradlew ktlintCheck --no-daemon

      - name: Detekt
        run: ./gradlew detekt --no-daemon

      - name: Unit Tests
        run: ./gradlew test --no-daemon

      - name: Integration Tests
        run: ./gradlew integrationTest --no-daemon

      - name: Architecture Tests (Konsist)
        run: ./gradlew :shared:testing:test --no-daemon
```

### Nightly Pipeline (Deep Validation - ~2.5h)

**.github/workflows/nightly.yml:**
```yaml
name: Nightly - Deep Validation

on:
  schedule:
    - cron: '0 2 * * *'  # 2 AM daily
  workflow_dispatch:

jobs:
  deep-validation:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Property-Based Tests
        run: ./gradlew propertyTest --no-daemon

      - name: Fuzz Tests (7 targets × 5 min)
        run: ./gradlew fuzzTest --no-daemon

      - name: Concurrency Tests (LitmusKt)
        run: ./gradlew litmusTest --no-daemon

      - name: Mutation Testing (Pitest)
        run: ./gradlew pitest --no-daemon
```

### Security Review Pipeline

**.github/workflows/security-review.yml:**
```yaml
name: Security Review

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  schedule:
    - cron: '0 0 * * 0'  # Weekly on Sunday

jobs:
  owasp-dependency-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: OWASP Dependency Check
        run: ./gradlew dependencyCheckAnalyze --no-daemon

      - name: Upload Report
        uses: actions/upload-artifact@v4
        with:
          name: owasp-report
          path: build/reports/dependency-check-report.html
```

---

## Implementation Checklist

- [ ] Create .github/workflows/ directory
- [ ] Implement ci.yml (fast feedback pipeline)
- [ ] Implement nightly.yml (deep validation pipeline)
- [ ] Implement security-review.yml (OWASP scanning)
- [ ] Configure OWASP Dependency Check plugin in Gradle
- [ ] Test CI pipeline with intentional failure
- [ ] Verify pipeline execution time <15min for CI
- [ ] Configure pipeline status badges in README.md
- [ ] Commit: "Add CI/CD pipelines for quality and security validation"

---

## Test Evidence

- [ ] CI pipeline runs on push to main
- [ ] CI pipeline runs on pull requests
- [ ] CI pipeline completes in <15 minutes
- [ ] Nightly pipeline scheduled (cron)
- [ ] Security review runs weekly
- [ ] OWASP dependency report generated
- [ ] All pipelines visible in GitHub Actions
- [ ] Intentional failure correctly fails build

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] All 3 pipelines implemented and tested
- [ ] CI execution time <15 minutes
- [ ] Pipelines run on correct triggers
- [ ] Pipeline status visible in GitHub
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 1.8 - Spring Modulith Boundary Enforcement
**Next Story:** Story 1.10 - Git Hooks for Quality Gates

---

## References

- PRD: FR008 (Quality Gates), NFR002 (Security)
- Architecture: Section 3 (CI/CD Pipelines)
- Tech Spec: Section 3 (FR008 Implementation)
