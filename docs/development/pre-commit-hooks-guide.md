# Pre-Commit Hooks Guide

**Story**: 8.2 - Pre-Commit Hook Infrastructure
**Status**: Task 1 Complete - Infrastructure installed

---

## What Are Pre-Commit Hooks?

Automated validation that runs **before** each commit to catch issues locally (< 30 seconds) instead of waiting for CI (15 minutes).

---

## Installation

**Automatic**: Hooks install during `./scripts/init-dev.sh`

**Manual**:
```bash
./gradlew installGitHooks
```

---

## What Runs Pre-Commit?

**(Tasks 2-6 Pending - Current: Placeholder only)**

**Planned Validation** (when Tasks 2-6 complete):
1. **ktlint format** (~3s) - Code formatting
2. **Detekt static** (~5s) - Code quality
3. **Konsist naming** (~2s) - Test naming (Story 8.1)
4. **Unit tests** (~15s) - Changed modules only
5. **Commit message** (<1s) - Format validation

**Target**: < 30 seconds total

---

## Bypassing Hooks (Emergency Use Only)

### When to Bypass

**Legitimate bypass scenarios**:
- Emergency production hotfix
- WIP commits for collaboration
- Merge conflict resolution
- Generated code commits

### How to Bypass

```bash
# Skip pre-commit validation
git commit --no-verify -m "[Epic 8] emergency fix"

# Or short form
git commit -n -m "WIP: testing feature"
```

### Bypass Tracking

**Note**: Bypassed commits are tracked for metrics (Story 8.2 AC14). High bypass rates (>20%) trigger investigation.

---

## Troubleshooting

### Hook Not Running

**Check installation**:
```bash
ls -la .git/hooks/pre-commit
# Should be executable (rwxr--r--)
```

**Reinstall**:
```bash
./gradlew installGitHooks
```

### Hook Too Slow (>30 seconds)

**Current**: Placeholder only (fast)

**When Tasks 2-6 complete**: If hooks exceed 30s:
1. Check which validation is slow: metrics logged to `.git/hooks/metrics.log`
2. Optimize slow check or skip for large commits
3. Report to team (may need configuration tuning)

### Hook Blocks Valid Commit

**Temporary bypass**:
```bash
git commit --no-verify -m "..."
```

**Report issue**: If hooks incorrectly block valid work, report to team for rule adjustment

---

## Uninstalling Hooks

```bash
./gradlew uninstallGitHooks
```

---

## Development Status

**Task 1 Complete** (AC1-4): Infrastructure installed ✅
- Technology: Gradle-native git hooks
- Installation: Auto via init-dev.sh
- Bypass: --no-verify documented

**Tasks 2-6 Pending**:
- Validation logic (ktlint, Detekt, Konsist, tests)
- Commit message validation
- Progress output & error messages
- Metrics collection & dashboard

**Expected completion**: Story 8.2 implementation (9-10 days)

---

## References

- Story 8.2: docs/stories/8.2.establish-pre-commit-hook-infrastructure.story.md
- Technology Decision: docs/architecture/pre-commit-technology-decision.md
- Winston's Validation: docs/qa/assessments/8.2-architectural-validation-20251005.md
