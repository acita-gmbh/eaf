#!/usr/bin/env bash
set -euo pipefail

# Mirrors .github/workflows/test.yml locally for debugging.
# References ci-burn-in.md + selective-testing.md guidance.

BASE_REF=${BASE_REF:-origin/main}
HEAD_REF=${HEAD_REF:-HEAD}

echo "🚀 Running selective guard (diff vs ${BASE_REF})"
BASE_REF="$BASE_REF" HEAD_REF="$HEAD_REF" ./scripts/test-changed.sh

echo "🧱 Running build + static analysis"
./gradlew assemble ktlintCheck detekt --no-daemon --stacktrace

echo "🧪 Running full CI suites"
./gradlew ciTests integrationTest :shared:testing:test --no-daemon --stacktrace

if command -v shellcheck >/dev/null 2>&1; then
    echo "🔎 Shell script analysis"
    find scripts/ -name "*.sh" -print0 | xargs -0 shellcheck
else
    echo "⚠️  shellcheck not found, skipping shell lint."
fi

echo "🔥 Local burn-in (3 iterations, ciTests only)"
BURN_IN_ITERATIONS=${BURN_IN_ITERATIONS:-3} \
    BURN_IN_TASKS=${BURN_IN_TASKS:-"ciTests"} \
    ./scripts/burn-in.sh

echo "✅ Local CI pipeline completed."
