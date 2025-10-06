#!/bin/bash
# Story 8.2: Validate commit message format (AC10)
# Usage: ./scripts/git/validate-commit-msg.sh <commit-msg-file>

set -euo pipefail

COMMIT_MSG_FILE="${1:-.git/COMMIT_EDITMSG}"
COMMIT_MSG=$(cat "$COMMIT_MSG_FILE" 2>/dev/null || echo "")

if [ -z "$COMMIT_MSG" ]; then
    echo "❌ Empty commit message"
    exit 1
fi

# AC10: Pattern validation
# Required: [JIRA-XXX] type: description or [Epic X] type: description
# Types: feat, fix, docs, style, refactor, test, chore

JIRA_PATTERN='^\[(DPCMSG-[0-9]+|Epic [0-9]+)\] (feat|fix|docs|style|refactor|test|chore): .+'

if echo "$COMMIT_MSG" | grep -Eq "$JIRA_PATTERN"; then
    echo "✓ Commit message format valid"
    exit 0
fi

# AC15: WIP commits bypass validation
if echo "$COMMIT_MSG" | grep -Eq '^WIP:'; then
    echo "⚠️  WIP commit detected - bypassing validation"
    echo "   Remember to use proper format for final commit"
    exit 0
fi

# Validation failed - show helpful error
echo ""
echo "❌ Invalid commit message format"
echo ""
echo "Required format:"
echo "  [JIRA-XXX] type: description"
echo "  [Epic X] type: description"
echo ""
echo "Types: feat, fix, docs, style, refactor, test, chore"
echo ""
echo "Examples:"
echo "  [DPCMSG-1234] feat: add pre-commit hooks"
echo "  [Epic 8] fix: resolve ktlint violations"
echo ""
echo "Your message:"
echo "  $COMMIT_MSG"
echo ""
echo "Bypass: git commit --no-verify (emergencies only)"
echo ""

exit 1
