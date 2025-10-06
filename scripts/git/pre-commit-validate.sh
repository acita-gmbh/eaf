#!/bin/bash
# Story 8.2: Pre-commit validation orchestrator
# This script is called by the .git/hooks/pre-commit hook.

set -euo pipefail

# --- Configuration ---
ROOT_DIR=$(git rev-parse --show-toplevel)
HOOK_DIR="$ROOT_DIR/.git/hooks"
SCRIPTS_DIR="$ROOT_DIR/scripts/git"
CONFIG_FILE="$ROOT_DIR/.pre-commit-config.yml" # Placeholder for future use

# --- Colors and Logging ---
RED='''[0;31m'''
GREEN='''[0;32m'''
YELLOW='''[1;33m'''
BLUE='''[0;34m'''
NC='''[0m''' # No Color

log_step() {
    printf "${BLUE}🔍 Pre-commit validation (%d/%d): %s...${NC}
" "$1" "$2" "$3"
}

log_success() {
    printf "${GREEN}✅ %s${NC} (%s)
" "$1" "$2"
}

log_failure() {
    printf "${RED}❌ %s${NC}
" "$1"
    printf "${YELLOW}%s${NC}
" "$2"
    exit 1
}

log_info() {
    printf "   ${NC}%s${NC}
" "$1"
}

# --- Main Execution ---
TOTAL_START_TIME=$(date +%s)
echo "🚀 EAF Pre-Commit Validation Starting..."
echo "=================================================="

# --- Step 1: ktlint format validation (AC5) ---
log_step 1 4 "Format checking (ktlint)"
STEP_START_TIME=$(date +%s)

STAGED_KOTLIN_FILES=$($SCRIPTS_DIR/get-staged-files.sh ".kt")
STAGED_GRADLE_FILES=$($SCRIPTS_DIR/get-staged-files.sh ".kts")
ALL_STAGED_KT_FILES="$STAGED_KOTLIN_FILES $STAGED_GRADLE_FILES"

if [ -z "$ALL_STAGED_KT_FILES" ]; then
    log_info "No Kotlin files staged, skipping."
    STEP_END_TIME=$(date +%s)
    STEP_DURATION=$((STEP_END_TIME - STEP_START_TIME))
    log_success "ktlint check passed" "${STEP_DURATION}s"
else
        if ! ./gradlew ktlintCheck --daemon --quiet > /dev/null 2>&1; then
        STEP_END_TIME=$(date +%s)
        STEP_DURATION=$((STEP_END_TIME - STEP_START_TIME))
        log_failure "ktlint format violations found." "Run './gradlew ktlintFormat' to fix."
    else
        STEP_END_TIME=$(date +%s)
        STEP_DURATION=$((STEP_END_TIME - STEP_START_TIME))
        log_success "ktlint check passed" "${STEP_DURATION}s"
    fi
fi

# --- Step 2: Detekt static analysis (AC6) ---
log_step 2 4 "Static analysis (Detekt)"
STEP_START_TIME=$(date +%s)

if [ -z "$ALL_STAGED_KT_FILES" ]; then
    log_info "No Kotlin files staged, skipping."
    STEP_END_TIME=$(date +%s)
    STEP_DURATION=$((STEP_END_TIME - STEP_START_TIME))
    log_success "Detekt check passed" "${STEP_DURATION}s"
else
    if ./gradlew detekt --daemon --quiet -Pdetekt.config.files="$ROOT_DIR/config/detekt/detekt-precommit.yml" > /dev/null 2>&1; then
        STEP_END_TIME=$(date +%s)
        STEP_DURATION=$((STEP_END_TIME - STEP_START_TIME))
        log_success "Detekt check passed" "${STEP_DURATION}s"
    else
        log_failure "Detekt violations found." "Run './gradlew detekt' and fix issues. You can see the report at build/reports/detekt/detekt.html"
    fi
fi


# --- Step 3: Konsist test naming validation (AC7) ---
log_step 3 4 "Test naming validation (Konsist)"
STEP_START_TIME=$(date +%s)

STAGED_TEST_FILES=$($SCRIPTS_DIR/get-staged-files.sh "Test.kt")
if [ -z "$STAGED_TEST_FILES" ]; then
    log_info "No test files staged, skipping."
    STEP_END_TIME=$(date +%s)
    STEP_DURATION=$((STEP_END_TIME - STEP_START_TIME))
    log_success "Konsist check passed" "${STEP_DURATION}s"
else
    # Native Kotest runner doesn't support --tests flag, so run all naming tests
    if ./gradlew :shared:testing:jvmKotest --daemon --quiet > /dev/null 2>&1; then
        STEP_END_TIME=$(date +%s)
        STEP_DURATION=$((STEP_END_TIME - STEP_START_TIME))
        log_success "Konsist check passed" "${STEP_DURATION}s"
    else
        log_failure "Konsist test naming violations found." "Fix test names according to Story 8.1 standards."
    fi
fi

# --- Step 4: Smart unit tests (AC8) ---
log_step 4 4 "Unit tests (changed modules)"
STEP_START_TIME=$(date +%s)

# The run-module-tests.sh script handles everything.
TEST_OUTPUT=$($SCRIPTS_DIR/run-module-tests.sh)
STEP_END_TIME=$(date +%s)
STEP_DURATION=$((STEP_END_TIME - STEP_START_TIME))

if [ $? -eq 0 ]; then
    log_info "$TEST_OUTPUT"
    log_success "Unit tests passed" "${STEP_DURATION}s"
else
    log_failure "Unit tests failed." "$TEST_OUTPUT"
fi


# --- Finalization ---
TOTAL_END_TIME=$(date +%s)
TOTAL_DURATION=$((TOTAL_END_TIME - TOTAL_START_TIME))

echo "=================================================="
echo "✅ All EAF pre-commit checks passed! (${TOTAL_DURATION}s total)"

# AC14: Log metrics
echo "$(date -Iseconds) - ${TOTAL_DURATION}s - PASS" >> "$HOOK_DIR/metrics.log"

exit 0