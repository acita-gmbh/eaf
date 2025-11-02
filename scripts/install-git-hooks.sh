#!/bin/bash
set -e

# EAF v1.0 Git Hooks Installer
# =============================
# Story 1.6: One-Command Initialization Script
# Installs pre-commit and pre-push hooks for quality enforcement

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_step() {
    echo -e "  ${BLUE}▶${NC} $1"
}

print_success() {
    echo -e "  ${GREEN}✅${NC} $1"
}

print_error() {
    echo -e "  ${RED}❌${NC} $1"
}

print_warning() {
    echo -e "  ${YELLOW}⚠️${NC} $1"
}

print_info() {
    echo -e "  ${BLUE}ℹ️${NC} $1"
}

# Function to check if a custom hook exists (not a sample)
has_custom_hook() {
    local HOOK_PATH="$1"
    if [ -f "$HOOK_PATH" ] && [ ! -L "$HOOK_PATH" ]; then
        # File exists and is not a symlink - check if it's not a sample
        if ! grep -q "\.sample" <<< "$HOOK_PATH"; then
            return 0  # Custom hook exists
        fi
    fi
    return 1  # No custom hook
}

# Function to install a Git hook
install_hook() {
    local HOOK_NAME="$1"
    local HOOK_CONTENT="$2"
    local HOOK_PATH=".git/hooks/$HOOK_NAME"

    # Check for existing custom hook (Constraint C8)
    if has_custom_hook "$HOOK_PATH"; then
        print_warning "Custom $HOOK_NAME hook already exists"
        echo ""
        read -p "  Overwrite existing $HOOK_NAME hook? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Skipping $HOOK_NAME installation (keeping existing hook)"
            return 1
        fi
        print_step "Backing up existing $HOOK_NAME to $HOOK_PATH.backup"
        cp "$HOOK_PATH" "$HOOK_PATH.backup"
    fi

    # Install the hook
    echo "$HOOK_CONTENT" > "$HOOK_PATH"
    chmod +x "$HOOK_PATH"
    print_success "$HOOK_NAME installed"
    return 0
}

# Main installation
main() {
    echo "Git Hooks Installation"
    echo "====================="
    echo ""
    print_info "Installing quality enforcement hooks..."
    echo ""

    # Verify we're in a Git repository
    if [ ! -d ".git" ]; then
        print_error "Not in a Git repository root"
        echo "Please run this script from the project root directory"
        exit 1
    fi

    # Pre-commit hook: Run ktlint format check
    print_step "Installing pre-commit hook..."
    PRE_COMMIT_HOOK='#!/bin/bash
# EAF v1.0 Pre-commit Hook
# Enforces code formatting with ktlint

echo "🔍 Running pre-commit checks..."

# Get list of staged Kotlin files
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACMR | grep "\.kt$" || true)

if [ -z "$STAGED_FILES" ]; then
    echo "✅ No Kotlin files staged, skipping ktlint check"
    exit 0
fi

echo "📝 Checking Kotlin code style with ktlint..."

# Run ktlint check on staged files
if ./gradlew ktlintCheck --quiet; then
    echo "✅ Code style check passed"
    exit 0
else
    echo "❌ Code style violations detected"
    echo ""
    echo "Fix with: ./gradlew ktlintFormat"
    echo "Then stage changes and commit again"
    echo ""
    echo "To bypass (not recommended): git commit --no-verify"
    exit 1
fi'

    if install_hook "pre-commit" "$PRE_COMMIT_HOOK"; then
        print_info "Pre-commit hook runs: ktlint format check"
    fi
    echo ""

    # Pre-push hook: Run Detekt and tests
    print_step "Installing pre-push hook..."
    PRE_PUSH_HOOK='#!/bin/bash
# EAF v1.0 Pre-push Hook
# Enforces static analysis and tests

echo "🔍 Running pre-push checks..."

# Run Detekt static analysis
echo "🔬 Running Detekt static analysis..."
if ! ./gradlew detekt --quiet; then
    echo "❌ Detekt violations detected"
    echo ""
    echo "Review report: build/reports/detekt/detekt.html"
    echo "Fix issues and push again"
    echo ""
    echo "To bypass (not recommended): git push --no-verify"
    exit 1
fi

# Run tests
echo "🧪 Running tests..."
if ! ./gradlew test --quiet; then
    echo "❌ Tests failed"
    echo ""
    echo "Fix failing tests and push again"
    echo ""
    echo "To bypass (not recommended): git push --no-verify"
    exit 1
fi

echo "✅ All pre-push checks passed"
exit 0'

    if install_hook "pre-push" "$PRE_PUSH_HOOK"; then
        print_info "Pre-push hook runs: Detekt + tests"
    fi
    echo ""

    # Final summary
    print_success "Git hooks installation complete"
    echo ""
    echo "📋 Installed Hooks:"
    echo "  - pre-commit:  ktlint format check"
    echo "  - pre-push:    Detekt static analysis + tests"
    echo ""
    echo "⚠️  Important:"
    echo "  - Hooks run automatically on commit/push"
    echo "  - To bypass (not recommended): use --no-verify flag"
    echo "  - Story 1.10 will implement comprehensive hook suite"
    echo ""
}

# Execute main function
main "$@"
