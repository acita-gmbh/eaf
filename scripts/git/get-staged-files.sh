#!/bin/bash
# Story 8.2: Get staged files for pre-commit validation
# Usage: ./scripts/git/get-staged-files.sh [extension]
# Example: ./scripts/git/get-staged-files.sh kt

set -euo pipefail

EXTENSION="${1:-.kt}"

# Get staged files with specified extension
# --cached: staged files only
# --name-only: just filenames
# --diff-filter=ACM: Added, Copied, Modified (not Deleted)
git diff --cached --name-only --diff-filter=ACM | grep "\\${EXTENSION}\$" || true
