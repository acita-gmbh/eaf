#!/usr/bin/env bash
set -euo pipefail

# Corpus Size Monitoring Script
# Story 3.12: Security Fuzz Testing with Jazzer
#
# Purpose: Monitor fuzzing corpus growth and alert on size thresholds
# Usage: ./scripts/check-corpus-size.sh [--cleanup]
#
# Thresholds:
# - WARNING: 50MB total corpus size
# - ERROR: 100MB total corpus size
# - Individual file limit: 1MB

CORPUS_ROOT="framework/security/corpus"
TOTAL_SIZE_WARNING_MB=50
TOTAL_SIZE_ERROR_MB=100
INDIVIDUAL_FILE_LIMIT_MB=1

echo "🔍 Checking fuzzing corpus size..."
echo ""

# Check if corpus directory exists
if [ ! -d "$CORPUS_ROOT" ]; then
    echo "❌ ERROR: Corpus directory not found: $CORPUS_ROOT"
    exit 1
fi

# Calculate total corpus size
TOTAL_SIZE_KB=$(du -sk "$CORPUS_ROOT" | cut -f1)
TOTAL_SIZE_MB=$((TOTAL_SIZE_KB / 1024))

echo "📊 Corpus Statistics:"
echo "  Total size: ${TOTAL_SIZE_MB}MB (${TOTAL_SIZE_KB}KB)"

# Count files per target
for target_dir in "$CORPUS_ROOT"/*; do
    if [ -d "$target_dir" ]; then
        TARGET_NAME=$(basename "$target_dir")
        FILE_COUNT=$(find "$target_dir" -type f | wc -l | tr -d ' ')
        TARGET_SIZE_KB=$(du -sk "$target_dir" | cut -f1)
        TARGET_SIZE_MB=$((TARGET_SIZE_KB / 1024))
        echo "  $TARGET_NAME: $FILE_COUNT files (${TARGET_SIZE_MB}MB)"
    fi
done

echo ""

# Check for large individual files
LARGE_FILES=$(find "$CORPUS_ROOT" -type f -size +${INDIVIDUAL_FILE_LIMIT_MB}M 2>/dev/null || true)
if [ -n "$LARGE_FILES" ]; then
    echo "⚠️  WARNING: Found large corpus files (>${INDIVIDUAL_FILE_LIMIT_MB}MB):"
    echo "$LARGE_FILES" | while read -r file; do
        SIZE=$(du -h "$file" | cut -f1)
        echo "  - $file ($SIZE)"
    done
    echo ""
fi

# Check total size thresholds
if [ "$TOTAL_SIZE_MB" -ge "$TOTAL_SIZE_ERROR_MB" ]; then
    echo "❌ ERROR: Corpus size exceeds ${TOTAL_SIZE_ERROR_MB}MB threshold"
    echo ""
    echo "Recommended actions:"
    echo "  1. Run cleanup: $0 --cleanup"
    echo "  2. Review corpus quality and remove redundant files"
    echo "  3. Consider reducing fuzzer duration if corpus is not valuable"
    exit 1
elif [ "$TOTAL_SIZE_MB" -ge "$TOTAL_SIZE_WARNING_MB" ]; then
    echo "⚠️  WARNING: Corpus size approaching ${TOTAL_SIZE_ERROR_MB}MB limit (currently ${TOTAL_SIZE_MB}MB)"
    echo ""
    echo "Consider running: $0 --cleanup"
    exit 0
else
    echo "✅ Corpus size is healthy (${TOTAL_SIZE_MB}MB / ${TOTAL_SIZE_ERROR_MB}MB limit)"
fi

# Cleanup mode (if --cleanup flag provided)
if [ "${1:-}" = "--cleanup" ]; then
    echo ""
    echo "🧹 Starting corpus cleanup..."

    CLEANED=0

    # Remove large auto-generated files (>1MB)
    LARGE_AUTO_FILES=$(find "$CORPUS_ROOT" -type f -size +${INDIVIDUAL_FILE_LIMIT_MB}M 2>/dev/null || true)
    if [ -n "$LARGE_AUTO_FILES" ]; then
        echo "$LARGE_AUTO_FILES" | while read -r file; do
            # Only delete if not in git (auto-generated)
            if ! git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
                echo "  Removing large file: $file"
                rm -f "$file"
                CLEANED=$((CLEANED + 1))
            fi
        done
    fi

    # Remove old auto-generated files (>90 days, not in git)
    OLD_FILES=$(find "$CORPUS_ROOT" -type f -mtime +90 2>/dev/null || true)
    if [ -n "$OLD_FILES" ]; then
        echo "$OLD_FILES" | while read -r file; do
            # Only delete if not in git (auto-generated)
            if ! git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
                echo "  Removing old file: $file (>90 days)"
                rm -f "$file"
                CLEANED=$((CLEANED + 1))
            fi
        done
    fi

    if [ "$CLEANED" -gt 0 ]; then
        echo "✅ Cleanup complete: Removed $CLEANED files"

        # Recalculate size after cleanup
        NEW_SIZE_KB=$(du -sk "$CORPUS_ROOT" | cut -f1)
        NEW_SIZE_MB=$((NEW_SIZE_KB / 1024))
        SAVED_MB=$((TOTAL_SIZE_MB - NEW_SIZE_MB))
        echo "📊 New size: ${NEW_SIZE_MB}MB (saved ${SAVED_MB}MB)"
    else
        echo "✅ No files to clean (all corpus files are recent or git-tracked seeds)"
    fi
fi

echo ""
echo "ℹ️  For more information, see: framework/security/corpus/README.md"
