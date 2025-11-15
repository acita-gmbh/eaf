#!/bin/bash
set -e

# EAF v1.0 Git Hooks Installer (Story 1.10)
# Copies templates from .git-hooks/ into .git/hooks/ with safety checks

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_step()   { echo -e "  ${BLUE}▶${NC} $1"; }
print_success(){ echo -e "  ${GREEN}✅${NC} $1"; }
print_error() { echo -e "  ${RED}❌${NC} $1"; }
print_warning(){ echo -e "  ${YELLOW}⚠️${NC} $1"; }
print_info()  { echo -e "  ${BLUE}ℹ️${NC} $1"; }

has_custom_hook() {
    local hook_path="$1"
    if [ -f "$hook_path" ] && [ ! -L "$hook_path" ]; then
        if ! grep -q "\.sample" <<<"$hook_path"; then
            return 0
        fi
    fi
    return 1
}

install_hook() {
    local hook_name="$1"
    local template_path=".git-hooks/$hook_name"
    local target_path=".git/hooks/$hook_name"

    if [ ! -f "$template_path" ]; then
        print_error "Template $template_path not found"
        echo "Install aborted – ensure .git-hooks templates are checked in"
        exit 1
    fi

    if has_custom_hook "$target_path"; then
        print_warning "Custom $hook_name hook already exists"
        echo ""
        read -p "  Overwrite existing $hook_name hook? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Skipping $hook_name installation (keeping existing hook)"
            return 1
        fi
        print_step "Backing up existing $hook_name to $target_path.backup"
        cp "$target_path" "$target_path.backup"
    fi

    cp "$template_path" "$target_path"
    chmod +x "$target_path"
    print_success "$hook_name installed"
}

main() {
    echo "Git Hooks Installation"
    echo "====================="
    echo ""
    print_info "Installing quality enforcement hooks..."
    echo ""

    if [ ! -d ".git" ]; then
        print_error "Not in a Git repository root"
        echo "Please run this script from the project root directory"
        exit 1
    fi

    if [ ! -d ".git-hooks" ]; then
        print_error "Directory .git-hooks/ not found"
        echo "Ensure templates are committed before running the installer"
        exit 1
    fi

    mkdir -p .git/hooks

    print_step "Installing pre-commit hook..."
    install_hook "pre-commit" && print_info "Pre-commit: ktlintFormat + detekt + shellcheck (staged files only)"
    echo ""

    print_step "Installing pre-push hook..."
    install_hook "pre-push" && print_info "Pre-push: ktlintCheck + detekt + test (all modules)"
    echo ""

    print_success "Git hooks installation complete"
    echo ""
    echo "📋 Installed Hooks (Story 1.4 - 2-Stage Validation):"
    echo "  - pre-commit:  Incremental quality gates (20s-2.5min)"
    echo "      • ktlintFormat (auto-fixes & auto-stages)"
    echo "      • detekt (affected modules only)"
    echo "      • shellcheck (staged scripts)"
    echo ""
    echo "  - pre-push:    Comprehensive validation (2-5min)"
    echo "      • ktlintCheck (all modules)"
    echo "      • detekt (all modules)"
    echo "      • test (unit tests, all modules)"
    echo "      • Note: Integration tests run in CI"
    echo ""
    echo "⚠️  Important:"
    echo "  - Hooks run automatically on commit/push"
    echo "  - To bypass (not recommended): git commit/push --no-verify"
    echo "  - Authoritative hook templates live in .git-hooks/; keep changes in sync there"
    echo "  - shellcheck required: brew install shellcheck (macOS) or apt install shellcheck (Linux)"
    echo ""
    echo "💡 Workflow:"
    echo "  1. git add <files>          → triggers pre-commit (incremental)"
    echo "  2. git commit               → auto-formatting + re-staging"
    echo "  3. git push                 → triggers pre-push (comprehensive)"
    echo "  4. CI validates fully       → integration tests + security scans"
    echo ""
}

main "$@"
