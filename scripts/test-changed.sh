#!/usr/bin/env bash
set -euo pipefail

# Selective Gradle execution based on changed files.
# Inspired by selective-testing.md + ci-burn-in.md patterns.
# Usage: BASE_REF=origin/main HEAD_REF=HEAD ./scripts/test-changed.sh

BASE_REF=${1:-${BASE_REF:-origin/main}}
HEAD_REF=${2:-${HEAD_REF:-HEAD}}
FORCE_FULL_SUITE=${FORCE_FULL_SUITE:-false}

if ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
    echo "⚠️  Base ref '$BASE_REF' not found. Falling back to origin/main." >&2
    BASE_REF="origin/main"
fi

readarray -t CHANGED_FILES < <(git diff --name-only "$BASE_REF"..."$HEAD_REF" 2>/dev/null || git diff --name-only "$BASE_REF" 2>/dev/null)

if [ "${#CHANGED_FILES[@]}" -eq 0 ]; then
    echo "ℹ️  No changed files detected between $BASE_REF and $HEAD_REF."
fi

docs_only=true
run_ci_tests=false
run_integration=false
run_arch=false
run_shell_lint=false

for file in "${CHANGED_FILES[@]}"; do
    [[ -z "$file" ]] && continue

    case "$file" in
        shared/testing/*|architecture/*)
            docs_only=false
            run_arch=true
            ;;
        docs/architecture/*)
            docs_only=false
            run_arch=true
            ;;
        */integration/*|*IntegrationTest*.kt|*integrationTest*|*integration-test*)
            docs_only=false
            run_integration=true
            ;;
        scripts/*.sh)
            docs_only=false
            run_shell_lint=true
            ;;
        docs/*|README.md|README*.md|*.md|*.rst)
            continue
            ;;
        *.kt|*.kts|*.java|gradle/**|gradle.*|build.gradle*|settings.gradle*|framework/*|shared/*|products/*|tools/*)
            docs_only=false
            run_ci_tests=true
            ;;
        *)
            docs_only=false
            ;;
    esac
done

tasks=()
declare -A seen=()

if [ "$run_ci_tests" = true ]; then
    tasks+=("test")
    seen["test"]=1
fi
if [ "$run_integration" = true ] && [ -z "${seen[\"integrationTest\"]:-}" ]; then
    tasks+=("integrationTest")
    seen["integrationTest"]=1
fi
if [ "$run_arch" = true ] && [ -z "${seen[\"konsistTest\"]:-}" ]; then
    tasks+=("konsistTest")
    seen["konsistTest"]=1
fi

if [ "$FORCE_FULL_SUITE" = true ]; then
    docs_only=false
    if [ "${#tasks[@]}" -eq 0 ]; then
        tasks=("test" "integrationTest" "konsistTest")
    fi
fi

if [ "$docs_only" = true ]; then
    echo "📝 Docs/config-only change detected. Running ktlintCheck as lightweight guard."
    ./gradlew ktlintCheck --no-daemon --stacktrace
else
    if [ "${#tasks[@]}" -eq 0 ]; then
        echo "No targeted tasks derived from diff. Falling back to test."
        tasks=("test")
    fi

    echo "🔍 Selective execution triggered for tasks: ${tasks[*]}"
    ./gradlew "${tasks[@]}" --no-daemon --stacktrace

    if [ "$run_shell_lint" = true ]; then
        if command -v shellcheck >/dev/null 2>&1; then
            echo "🔎 Running shellcheck for modified shell scripts."
            find scripts/ -name "*.sh" -print0 | xargs -0 shellcheck
        else
            echo "⚠️  shellcheck is not installed; skipping shell script analysis." >&2
        fi
    fi
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
    if [ "$docs_only" = true ]; then
        echo "docs_only=true" >>"$GITHUB_OUTPUT"
    else
        echo "docs_only=false" >>"$GITHUB_OUTPUT"
    fi
fi
