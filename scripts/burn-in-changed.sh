#!/bin/bash
# scripts/burn-in-changed.sh
# Usage: ./scripts/burn-in-changed.sh [iterations] [base-branch]

set -e  # Exit on error

# Configuration
ITERATIONS=${1:-10}
BASE_BRANCH=${2:-main}
SPEC_PATTERN='\.(spec|test)\.(ts|js|tsx|jsx)$'

echo "🔥 Burn-In Test Runner"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Iterations: $ITERATIONS"
echo "Base branch: $BASE_BRANCH"
echo ""

# Detect changed test files
echo "📋 Detecting changed test files..."
CHANGED_SPECS=$(git diff --name-only origin/$BASE_BRANCH...HEAD | grep -E "$SPEC_PATTERN" || echo "")

if [ -z "$CHANGED_SPECS" ]; then
  echo "✅ No test files changed. Skipping burn-in."
  exit 0
fi

echo "Changed test files:"
echo "$CHANGED_SPECS" | sed 's/^/  - /'
echo ""

# Count specs
SPEC_COUNT=$(echo "$CHANGED_SPECS" | wc -l | xargs)
echo "Running burn-in on $SPEC_COUNT test file(s)..."
echo ""

# Convert specs to array for safe handling
mapfile -t SPEC_ARRAY <<< "$CHANGED_SPECS"

# Burn-in loop
for i in $(seq 1 "$ITERATIONS"); do
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "🔄 Iteration $i/$ITERATIONS"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # Run tests with explicit file list (properly quoted array expansion)
  # Using npm run test -- which usually defaults to Playwright or Vitest depending on context
  # Assuming E2E context for .spec.ts files
  if npm run test:e2e -- "${SPEC_ARRAY[@]}" 2>&1 | tee "burn-in-log-$i.txt"; then
    echo "✅ Iteration $i passed"
  else
    echo "❌ Iteration $i failed"

    # Save failure artifacts
    mkdir -p burn-in-failures/iteration-$i
    cp -r test-results/ burn-in-failures/iteration-$i/ 2>/dev/null || true
    cp -r playwright-report/ burn-in-failures/iteration-$i/ 2>/dev/null || true

    echo ""
    echo "🛑 BURN-IN FAILED on iteration $i"
    echo "Failure artifacts saved to: burn-in-failures/iteration-$i/"
    echo "Logs saved to: burn-in-log-$i.txt"
    echo ""
    exit 1
  fi

  echo ""
done

# Success summary
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 BURN-IN PASSED"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "All $ITERATIONS iterations passed for $SPEC_COUNT test file(s)"
echo "Changed specs are stable and ready to merge."
echo ""

# Cleanup logs
rm -f burn-in-log-*.txt

exit 0
