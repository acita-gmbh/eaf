#!/bin/bash
# Story 8.2: Pre-commit validation script (Tasks 2-5)
# Called by .git/hooks/pre-commit

set -euo pipefail

START_TIME=$(date +%s)

echo "🔍 EAF Pre-Commit Validation"
echo "=================================================="

# AC5: ktlint check on staged Kotlin files (Subtask 2.2)
STAGED_KT=$(git diff --cached --name-only --diff-filter=ACM | grep '\.kt$' || true)
if [ -n "$STAGED_KT" ]; then
    echo "[ ] Format checking (ktlint)..."
    echo "   Task 2.2 pending - full ktlint integration"
fi

# AC7: Konsist test naming validation (Subtask 2.4)
STAGED_TESTS=$(git diff --cached --name-only --diff-filter=ACM | grep '/test/.*\.kt$' || true)
if [ -n "$STAGED_TESTS" ]; then
    echo "[ ] Test naming validation (Story 8.1 Konsist)..."
    echo "   Task 2.4 pending - Konsist integration"
fi

# AC6: Detekt static analysis (Subtask 2.3)
echo "[ ] Static analysis (Detekt)..."
echo "   Task 2.3 pending - fast Detekt profile"

# AC8: Smart unit tests (Task 3)
echo "[ ] Unit tests (changed modules)..."
echo "   Task 3 pending - smart module detection"

# AC10: Commit message (Task 4)
echo "[ ] Commit message validation..."
echo "   Task 4 pending - format validation"

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "✅ Pre-commit validation complete (${DURATION}s)"
echo "   Tasks 2-6 implementation in progress"

# AC14: Log metrics
echo "$(date -Iseconds) - ${DURATION}s - PASS (partial)" >> .git/hooks/metrics.log

exit 0
