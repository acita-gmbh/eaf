# CI – Quality Gate Pipeline

This workflow complements `ci.yml` (fast feedback) and `nightly.yml` (deep validation) by providing a **repeatable, data-driven quality gate** whenever a PR targets `main`, when release branches push, or when someone manually/triggers the scheduled run. It follows the guidelines from:

- `ci-burn-in.md` for staged jobs, shard orchestration, and burn-in loops.
- `selective-testing.md` for diff-based execution.
- `visual-debugging.md` for artifact strategy.
- `test-quality.md` for deterministic, isolated suites.
- `playwright-config.md` (timeouts + artifact conventions, adapted for Gradle services).

## Workflow Triggers

| Event | Purpose |
| ----- | ------- |
| `pull_request` → `main` | Enforces quality gate before merge. |
| `push` → `main` / `release/**` | Protects main + release branches after direct pushes. |
| `workflow_dispatch` | Manual rebuild (optionally override burn-in iterations). |
| `schedule` (Mon/Thu 01:30 UTC) | Twice-weekly assurance run outside nightly fuzz/PBT suite. |

## Job Breakdown

1. **Selective Guard** (`scripts/test-changed.sh`)
   - Computes diff vs `origin/main`.
   - Runs targeted Gradle tasks (ciTests/integrationTest/architecture) plus shellcheck when scripts change.
   - Auto-falls back to full suite for non-PR events.
2. **Gradle Quality Matrix**
   - Matrix stages keep jobs <15 min and run in parallel:
     - `assemble ktlintCheck detekt`
     - `ciTests`
     - `integrationTest`
     - `:shared:testing:test` (architecture/konsist)
   - Failure-only artifact upload keeps storage lean but still captures investigation data.
3. **Shell Lint**
   - Dedicated job to run `shellcheck` with Ubuntu packages to ensure parity with `.github/workflows/ci.yml`.
4. **Burn-In Loop**
   - Runs `ciTests` + `integrationTest` through `scripts/burn-in.sh`.
   - Default 10 iterations (configurable via `workflow_dispatch` input) – one failure fails the job.

## Selective Testing Strategy

`scripts/test-changed.sh` enforces the selective-testing playbook:

- Docs-only change → short `ktlintCheck` guard; pipeline output `docs_only=true` and matrix/burn-in skip automatically.
- Kotlin/Gradle modules → run `ciTests`; integration-heavy paths → add `integrationTest`; architecture touches → add `:shared:testing:test`.
- Shell changes automatically trigger shellcheck (local + CI).
- `FORCE_FULL_SUITE=true` on non-PR events ensures scheduled + pushed runs still execute everything even if no diff exists.

## Burn-In Loop

The burn-in job is derived directly from `ci-burn-in.md`:

- `scripts/burn-in.sh` loops over tasks (`ciTests integrationTest`) for `BURN_IN_ITERATIONS` (default 10).
- Manual dispatch can override iterations via `burn_iterations`.
- Reports from every iteration are archived to `burn-in-reports/`.
- Fail-fast disabled upstream so artifacts are preserved for each shard.

## Local Reproduction

Run the full workflow locally via:

```bash
./scripts/ci-local.sh
```

This script mirrors the workflow order: selective guard → build/static checks → full suites → shellcheck → 3-iteration burn-in (tunable with `BURN_IN_ITERATIONS`/`BURN_IN_TASKS` env vars). Use it before pushing or when triaging CI issues.

## Artifact + Debugging Expectations

- All Gradle reports (`**/build/reports/tests`, `**/build/test-results`, `**/build/reports/{ktlint,detekt}`) are uploaded whenever a job fails (`visual-debugging.md` guidance).
- Failure-only retention (14 days) keeps storage manageable while leaving enough time for investigators.
- Download artifacts and open JUnit/HTML reports directly; pair with local `./scripts/ci-local.sh` + `./gradlew ... --info` for deeper traces.

## Relationship to Existing Workflows

- `ci.yml`: super-fast (≤20 min) push/PR loop. Continue using it for everyday commits.
- `nightly.yml`: heavyweight fuzz/PBT/perf/mutation tests at 02:00 UTC daily.
- `test.yml` (this workflow): day-time quality gate with selective front-runner + burn-in; use manual dispatch for release candidates or suspected flakes.
