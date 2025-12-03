#!/usr/bin/env bash
#
# Mutant-Kraken Experimental Runner for EAF Core Modules
#
# This script runs mutant-kraken on individual EAF framework modules.
# It's designed for experimental evaluation only - NOT production CI.
#
# Prerequisites:
#   cargo install mutant-kraken
#   OR
#   brew tap JosueMolinaMorales/mutant-kraken && brew install mutant-kraken
#
# Usage:
#   ./scripts/run-mutant-kraken.sh [module]
#
# Examples:
#   ./scripts/run-mutant-kraken.sh              # Run on all EAF core modules
#   ./scripts/run-mutant-kraken.sh eaf-core     # Run on eaf-core only
#   ./scripts/run-mutant-kraken.sh eaf-auth     # Run on eaf-auth only
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# EAF modules suitable for mutation testing (pure Kotlin, no Spring dependencies)
EAF_MODULES=(
    "eaf-core"
    "eaf-auth"
    "eaf-tenant"
)

# Modules with Spring dependencies (more complex, higher risk of timeouts)
EAF_SPRING_MODULES=(
    "eaf-eventsourcing"
    "eaf-auth-keycloak"
)

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    if ! command -v mutant-kraken &> /dev/null; then
        log_error "mutant-kraken not found!"
        echo ""
        echo "Install via Cargo:"
        echo "  cargo install mutant-kraken"
        echo ""
        echo "Or via Homebrew (macOS):"
        echo "  brew tap JosueMolinaMorales/mutant-kraken"
        echo "  brew install mutant-kraken"
        exit 1
    fi

    # Capture version output without letting set -e abort on failure
    local version_output
    version_output="$(mutant-kraken -v 2>&1)" || true

    # Test command success separately
    if mutant-kraken -v >/dev/null 2>&1; then
        log_info "mutant-kraken version: $version_output"
    else
        log_warn "Could not determine mutant-kraken version (command failed)"
        log_warn "Output: $version_output"
        log_warn "Continuing anyway, but you may encounter compatibility issues"
    fi
}

create_module_config() {
    local module=$1
    local module_dir="$PROJECT_ROOT/eaf/$module"
    local config_file="$module_dir/mutantkraken.config.json"

    if [[ ! -d "$module_dir" ]]; then
        log_error "Module directory does not exist: $module_dir"
        return 1
    fi

    if ! cat > "$config_file" << 'EOFCONFIG'
{
  "general": {
    "timeout": 180,
    "operators": [
      "NotNullAssertionOperator",
      "ElvisRemoveOperator",
      "ElvisLiteralChangeOperator",
      "WhenRemoveBranchOperator",
      "FunctionalReplacementOperator",
      "FunctionalBinaryReplacementOperator",
      "ArithmeticReplacementOperator",
      "LogicalReplacementOperator",
      "RelationalReplacementOperator"
    ]
  },
  "ignore": {
    "ignore_files": [
      "^.*Test\\.kt$",
      "^.*IntegrationTest\\.kt$",
      "^.*Spec\\.kt$",
      "^.*TestFixtures\\.kt$"
    ],
    "ignore_directories": [
      "build",
      "bin",
      ".gradle",
      ".idea",
      "gradle",
      "test",
      "mutant-kraken-dist"
    ]
  },
  "threading": {
    "max_threads": 2
  },
  "output": {
    "display_end_table": true
  },
  "logging": {
    "log_level": "INFO"
  }
}
EOFCONFIG
    then
        log_error "Failed to create config file: $config_file"
        log_error "Check write permissions and disk space"
        return 1
    fi

    log_info "Created config: $config_file"
}

run_module() {
    local module=$1
    local module_path="$PROJECT_ROOT/eaf/$module"
    local src_path="$module_path/src/main/kotlin"

    if [[ ! -d "$src_path" ]]; then
        log_warn "Source path not found: $src_path"
        return 1
    fi

    log_info "=========================================="
    log_info "Running mutant-kraken on: $module"
    log_info "=========================================="

    # Create module-specific config
    create_module_config "$module"

    # Change to module directory (mutant-kraken looks for gradlew in cwd or parent)
    cd "$module_path"

    # Run mutant-kraken
    # Note: This will use the project root's gradlew
    if mutant-kraken mutate "$src_path"; then
        log_success "$module: Mutation testing completed"

        # Move results to a module-specific location
        if [[ -d "mutant-kraken-dist" ]]; then
            local results_dir="$PROJECT_ROOT/mutant-kraken-results/$module"
            if ! mkdir -p "$results_dir"; then
                log_error "Failed to create results directory: $results_dir"
                return 1
            fi

            # Check if there are files to move (use ls instead of compgen for better compatibility with set -e)
            if ls mutant-kraken-dist/* >/dev/null 2>&1; then
                if ! mv mutant-kraken-dist/* "$results_dir/"; then
                    log_error "Failed to move results from mutant-kraken-dist to $results_dir"
                    log_error "Check disk space and permissions"
                    return 1
                fi
                log_info "Results moved to: $results_dir"
            else
                log_warn "No result files found in mutant-kraken-dist"
            fi
            rm -rf mutant-kraken-dist
        fi
    else
        log_error "$module: Mutation testing failed"
        return 1
    fi

    cd "$PROJECT_ROOT"
}

run_all_modules() {
    local modules=("${EAF_MODULES[@]}")
    local failed=()

    # Validate modules array is not empty
    if [[ ${#modules[@]} -eq 0 ]]; then
        log_error "No modules configured to test!"
        exit 1
    fi

    log_info "Running mutant-kraken on ${#modules[@]} EAF modules"
    echo ""

    for module in "${modules[@]}"; do
        if ! run_module "$module"; then
            failed+=("$module")
        fi
        echo ""
    done

    # Summary
    echo ""
    log_info "=========================================="
    log_info "SUMMARY"
    log_info "=========================================="

    if [[ ${#failed[@]} -gt 0 ]]; then
        log_error "Failed modules: ${failed[*]}"
        log_info "Results directory: $PROJECT_ROOT/mutant-kraken-results/"
        exit 1
    fi

    log_success "All modules completed successfully!"
    log_info "Results directory: $PROJECT_ROOT/mutant-kraken-results/"
}

show_help() {
    echo "Mutant-Kraken Experimental Runner for EAF"
    echo ""
    echo "Usage: $0 [module|--all|--spring|--help]"
    echo ""
    echo "Modules (pure Kotlin, recommended):"
    for m in "${EAF_MODULES[@]}"; do
        echo "  $m"
    done
    echo ""
    echo "Modules with Spring (higher timeout risk):"
    for m in "${EAF_SPRING_MODULES[@]}"; do
        echo "  $m"
    done
    echo ""
    echo "Options:"
    echo "  --all     Run on all pure Kotlin modules"
    echo "  --spring  Run on Spring modules (experimental)"
    echo "  --help    Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 eaf-core        # Run on eaf-core only"
    echo "  $0 --all           # Run on all pure Kotlin modules"
}

# Main
main() {
    cd "$PROJECT_ROOT"

    check_prerequisites

    if [[ $# -eq 0 ]] || [[ "$1" == "--all" ]]; then
        run_all_modules
    elif [[ "$1" == "--spring" ]]; then
        EAF_MODULES=("${EAF_SPRING_MODULES[@]}")
        run_all_modules
    elif [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
        show_help
    else
        # Single module
        if [[ -d "$PROJECT_ROOT/eaf/$1" ]]; then
            run_module "$1"
        else
            log_error "Module not found: $1"
            echo ""
            show_help
            exit 1
        fi
    fi
}

main "$@"
