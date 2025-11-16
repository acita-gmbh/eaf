#!/usr/bin/env bash
set -euo pipefail

# Regenerate Epic Stories - Batch Story & Context Generation
# Purpose: Regenerate all stories in an epic with fresh templates and context
# Author: BMad Method v6 (Story 4.0)
# Date: 2025-11-16

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly PROJECT_ROOT

# Usage information
usage() {
    cat <<EOF
${BLUE}Regenerate Epic Stories - Batch Story & Context Generation${NC}

${YELLOW}USAGE:${NC}
    $0 <epic-number> [options]

${YELLOW}ARGUMENTS:${NC}
    epic-number         Epic number (e.g., 4 for Epic 4)

${YELLOW}OPTIONS:${NC}
    --stories-only      Regenerate stories only (skip context generation)
    --contexts-only     Generate contexts only (skip story regeneration)
    --dry-run          Show what would be done without executing
    --help             Show this help message

${YELLOW}EXAMPLES:${NC}
    # Regenerate all Epic 4 stories and contexts
    $0 4

    # Regenerate Epic 5 stories only (no context)
    $0 5 --stories-only

    # Generate contexts for Epic 6 (stories already exist)
    $0 6 --contexts-only

    # Dry run to see what would happen
    $0 4 --dry-run

${YELLOW}PREREQUISITES:${NC}
    - BMad Method v6+ installed
    - Sprint status file exists (docs/sprint-status.yaml)
    - Epic stories exist in epics.md or epic files

${YELLOW}WORKFLOW:${NC}
    For each story in the epic:
    1. Run create-story workflow (#yolo mode)
       - Adds Tasks/Subtasks, Dev Agent Record, File List, Change Log
       - Preserves existing ACs and story content
    2. Run story-context workflow
       - Generates comprehensive Story Context XML
       - Includes architecture, dependencies, test patterns

${YELLOW}OUTPUT:${NC}
    - Updated story files: docs/sprint-artifacts/epic-<N>/story-<N>.<Y>-<name>.md
    - Context files: docs/sprint-artifacts/epic-<N>/<N>-<Y>-<name>.context.xml
    - Progress log: logs/regenerate-epic-<N>-<timestamp>.log

EOF
}

# Logging functions
log_info() {
    echo -e "${BLUE}ℹ${NC} $*"
}

log_success() {
    echo -e "${GREEN}✅${NC} $*"
}

log_warning() {
    echo -e "${YELLOW}⚠️${NC} $*"
}

log_error() {
    echo -e "${RED}❌${NC} $*" >&2
}

# Progress tracking
progress_bar() {
    local current=$1
    local total=$2
    local width=50
    local percentage=$((current * 100 / total))
    local filled=$((width * current / total))
    local empty=$((width - filled))

    printf "\r%bProgress:%b [" "${BLUE}" "${NC}"
    printf "%${filled}s" | tr ' ' '='
    printf "%${empty}s" | tr ' ' '-'
    printf "] %d%% (%d/%d)" "$percentage" "$current" "$total"
}

# Error handling
error_exit() {
    log_error "$1"
    exit 1
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check if running from project root
    if [[ ! -f "${PROJECT_ROOT}/settings.gradle.kts" ]]; then
        error_exit "Must run from EAF project root directory"
    fi

    # Check BMad installation
    if [[ ! -d "${PROJECT_ROOT}/.bmad" ]]; then
        error_exit "BMad Method not installed. Run: ./scripts/init-dev.sh"
    fi

    # Check sprint status file
    if [[ ! -f "${PROJECT_ROOT}/docs/sprint-status.yaml" ]]; then
        error_exit "Sprint status file not found: docs/sprint-status.yaml"
    fi

    log_success "Prerequisites satisfied"
}

# Get stories for epic
get_epic_stories() {
    local epic_num=$1

    log_info "Finding stories for Epic ${epic_num}..."

    # Parse sprint-status.yaml to get story keys
    # Story keys format: N-Y-name (e.g., "4-1-tenant-context-threadlocal")
    grep -E "^  ${epic_num}-[0-9]+-" "${PROJECT_ROOT}/docs/sprint-status.yaml" | \
                 awk -F':' '{print $1}' | \
                 sed 's/^  //' | \
                 grep -v "epic-${epic_num}:" | \
                 grep -v "epic-${epic_num}-retrospective" || true
}

# Regenerate single story
regenerate_story() {
    local story_key=$1
    local dry_run=$2

    log_info "Regenerating story: ${story_key}"

    if [[ "$dry_run" == "true" ]]; then
        log_warning "[DRY RUN] Would regenerate: ${story_key}"
        return 0
    fi

    # NOTE: This requires BMad workflow execution via Claude Code
    # For now, this is a placeholder that documents the process
    cat <<EOF

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📝 MANUAL STEP REQUIRED: Regenerate ${story_key}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Run this command in Claude Code:
    /bmad:bmm:workflows:create-story

When prompted:
    - Story to regenerate: ${story_key}
    - Mode: #yolo (automatic generation)

The workflow will:
    ✓ Preserve existing ACs and story content
    ✓ Add Tasks/Subtasks section
    ✓ Add Dev Agent Record section
    ✓ Add File List and Change Log

Press Enter when story regeneration is complete...
EOF

    read -r

    log_success "Story regenerated: ${story_key}"
}

# Generate story context
generate_context() {
    local story_key=$1
    local dry_run=$2

    log_info "Generating context for: ${story_key}"

    if [[ "$dry_run" == "true" ]]; then
        log_warning "[DRY RUN] Would generate context: ${story_key}"
        return 0
    fi

    # NOTE: This requires BMad workflow execution via Claude Code
    cat <<EOF

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 MANUAL STEP REQUIRED: Generate Context ${story_key}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Run this command in Claude Code:
    /bmad:bmm:workflows:story-context

When prompted:
    - Story: ${story_key}

The workflow will generate:
    ✓ Story Context XML with architecture references
    ✓ Code artifacts and dependencies
    ✓ Testing standards and patterns
    ✓ Fresh patterns from completed stories

Press Enter when context generation is complete...
EOF

    read -r

    log_success "Context generated: ${story_key}"
}

# Main execution
main() {
    local epic_num=""
    local stories_only=false
    local contexts_only=false
    local dry_run=false

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --stories-only)
                stories_only=true
                shift
                ;;
            --contexts-only)
                contexts_only=true
                shift
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            --help)
                usage
                exit 0
                ;;
            -*)
                error_exit "Unknown option: $1\nRun with --help for usage"
                ;;
            *)
                if [[ -z "$epic_num" ]]; then
                    epic_num=$1
                else
                    error_exit "Too many arguments\nRun with --help for usage"
                fi
                shift
                ;;
        esac
    done

    # Validate epic number
    if [[ -z "$epic_num" ]]; then
        error_exit "Epic number required\nRun with --help for usage"
    fi

    if ! [[ "$epic_num" =~ ^[0-9]+$ ]]; then
        error_exit "Epic number must be a positive integer"
    fi

    # Check for conflicting options
    if [[ "$stories_only" == "true" && "$contexts_only" == "true" ]]; then
        error_exit "Cannot use both --stories-only and --contexts-only"
    fi

    # Print header
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  Regenerate Epic ${epic_num} Stories${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    if [[ "$dry_run" == "true" ]]; then
        log_warning "DRY RUN MODE - No changes will be made"
        echo ""
    fi

    # Check prerequisites
    check_prerequisites

    # Get stories for epic
    local story_list
    story_list=$(get_epic_stories "$epic_num")

    if [[ -z "$story_list" ]]; then
        error_exit "No stories found for Epic ${epic_num} in sprint-status.yaml"
    fi

    # Convert to array
    local stories=()
    while IFS= read -r line; do
        stories+=("$line")
    done <<< "$story_list"

    local total=${#stories[@]}
    log_success "Found ${total} stories for Epic ${epic_num}"
    log_info "Processing ${total} stories..."
    echo ""

    # Process each story
    local current=0
    for story_key in "${stories[@]}"; do
        ((current++))
        progress_bar "$current" "$total"
        echo ""

        # Regenerate story
        if [[ "$contexts_only" != "true" ]]; then
            regenerate_story "$story_key" "$dry_run"
        fi

        # Generate context
        if [[ "$stories_only" != "true" ]]; then
            generate_context "$story_key" "$dry_run"
        fi

        echo ""
    done

    # Summary
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    log_success "Epic ${epic_num} regeneration complete!"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    log_info "Summary:"
    echo "  - Stories processed: ${total}"
    if [[ "$stories_only" != "true" ]]; then
        echo "  - Contexts generated: ${total}"
    fi
    if [[ "$contexts_only" != "true" ]]; then
        echo "  - Stories regenerated: ${total}"
    fi
    echo ""
    log_info "Next steps:"
    echo "  1. Review regenerated stories in docs/sprint-artifacts/epic-${epic_num}/"
    echo "  2. Verify context files generated: *.context.xml"
    echo "  3. Commit changes to feature branch"
    echo ""
}

# Run main
main "$@"
