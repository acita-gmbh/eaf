#!/bin/bash
# Story 8.2: Run unit tests for changed modules only (AC8)
# Usage: ./scripts/git/run-module-tests.sh

set -euo pipefail

# Detect modules from staged files
MODULES=$(./scripts/git/detect-modules.sh)

if [ -z "$MODULES" ]; then
    echo "No modules with code changes - skipping tests"
    exit 0
fi

echo "Testing changed modules: $(echo "$MODULES" | tr '\n' ' ')"

# Run fast tests only (no Testcontainers)
for MODULE in $MODULES; do
    # Convert :framework:admin-shell to framework/admin-shell
    MODULE_PATH=$(echo "$MODULE" | sed 's/:/\//g' | sed 's/^\///')

    # Skip npm packages (check for package.json instead of build.gradle.kts)
    if [ -f "$MODULE_PATH/package.json" ] && [ ! -f "$MODULE_PATH/build.gradle.kts" ]; then
        echo "[ ] Skipping $MODULE (npm package, not Gradle module)"
        continue
    fi

    echo "[ ] Testing $MODULE..."
    # Use -P fastTests=true to skip integration tests
    if ./gradlew "$MODULE:test" -P fastTests=true --quiet 2>/dev/null; then
        echo "   ✓ $MODULE tests passed"
    else
        echo "   ✗ $MODULE tests failed"
        exit 1
    fi
done

exit 0
