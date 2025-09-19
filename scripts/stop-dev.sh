#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="$PROJECT_ROOT/.pids"
COMPOSE_FILE="$PROJECT_ROOT/compose.yml"

if [[ -f "$PID_DIR/backend.pid" ]]; then
  kill "$(cat "$PID_DIR/backend.pid")" 2>/dev/null || true
  rm "$PID_DIR/backend.pid"
fi

if [[ -f "$PID_DIR/frontend.pid" ]]; then
  kill "$(cat "$PID_DIR/frontend.pid")" 2>/dev/null || true
  rm "$PID_DIR/frontend.pid"
fi

if command -v docker >/dev/null && [[ -f "$COMPOSE_FILE" ]]; then
  docker compose -f "$COMPOSE_FILE" down
fi

echo "EAF development stack stopped."
