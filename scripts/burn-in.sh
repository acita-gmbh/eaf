#!/usr/bin/env bash
set -euo pipefail

# Burn-in loop for Gradle test tasks (ci-burn-in.md reference)
# Environment variables:
#   BURN_IN_ITERATIONS (default 10)
#   BURN_IN_TASKS (space-separated, default "ciTests integrationTest")
#   GRADLE_ARGS (additional args, e.g. "--no-build-cache")

ITERATIONS=${BURN_IN_ITERATIONS:-10}
TASKS_STRING=${BURN_IN_TASKS:-"ciTests integrationTest"}
GRADLE_ARGS=${GRADLE_ARGS:-"--no-daemon --stacktrace"}

IFS=' ' read -r -a TASKS <<<"$TASKS_STRING"
IFS=' ' read -r -a EXTRA_ARGS <<<"$GRADLE_ARGS"

if [ "${#TASKS[@]}" -eq 0 ]; then
    echo "No tasks provided for burn-in. Exiting."
    exit 0
fi

echo "🔥 Burn-in loop starting (${ITERATIONS} iterations) for tasks: ${TASKS[*]}"

for task in "${TASKS[@]}"; do
    for iteration in $(seq 1 "$ITERATIONS"); do
        echo "➡️  Task ${task}: iteration ${iteration}/${ITERATIONS}"
        ./gradlew "$task" "${EXTRA_ARGS[@]}"
    done
done

echo "✅ Burn-in loop completed successfully."
