#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="$PROJECT_ROOT/.pids"
COMPOSE_FILE="$PROJECT_ROOT/compose.yml"
ENV_FILE="$PROJECT_ROOT/scripts/dev.env"
ENV_TEMPLATE="$PROJECT_ROOT/scripts/dev.env.example"

load_env_file() {
  if [[ -f "$ENV_FILE" ]]; then
    echo "Loading environment overrides from $ENV_FILE"
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  elif [[ -f "$ENV_TEMPLATE" ]]; then
    echo "Environment file $ENV_FILE not found. Copy $ENV_TEMPLATE and customize credentials."
  else
    echo "Environment file $ENV_FILE not found."
  fi
}

load_env_file

: "${KEYCLOAK_ADMIN:=admin}"
: "${KEYCLOAK_ADMIN_PASSWORD:=admin}"

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
