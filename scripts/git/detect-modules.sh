#!/bin/bash
# Story 8.2: Detect affected modules from staged files
# Usage: ./scripts/git/detect-modules.sh

set -euo pipefail

# Get staged files and extract module names
# Pattern: framework/security/... → framework:security
#          products/widget-demo/... → products:widget-demo
#          shared/testing/... → shared:testing

git diff --cached --name-only --diff-filter=ACM | \
  grep -E '(framework|products|shared|tools)/' | \
  sed -n 's|^\([^/]*\)/\([^/]*\)/.*|:\1:\2|p' | \
  sort -u || true
