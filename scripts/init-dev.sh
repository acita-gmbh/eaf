#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"
PID_DIR="$PROJECT_ROOT/.pids"
COMPOSE_FILE="$PROJECT_ROOT/compose.yml"
KEYCLOAK_REALM_FILE="$PROJECT_ROOT/shared/testing/src/main/resources/test-realm.json"
ENV_FILE="$PROJECT_ROOT/scripts/dev.env"
ENV_TEMPLATE="$PROJECT_ROOT/scripts/dev.env.example"
START_TIME=$(date +%s)

BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SKIP_TESTS=false
SKIP_TEST_DATA=false
DETACH=false
ACCEPT_DEFAULT_ADMIN=false

print_usage() {
  cat <<'USAGE'
Usage: ./scripts/init-dev.sh [options]

Options:
  --skip-tests         Skip running Constitutional TDD tasks (test, integrationTest, pitest)
  --skip-test-data     Skip loading optional SQL test data
  --detach             Do not tail background processes (leave running)
  --accept-default-admin Use default Keycloak admin password (admin) – not recommended
  --help               Show this help message
USAGE
}

log() {
  printf "%b%s%b\n" "$1" "$2" "$NC"
}

log_info() { log "$BLUE" "ℹ️  $1"; }
log_success() { log "$GREEN" "✅ $1"; }
log_warn() { log "$YELLOW" "⚠️  $1"; }
log_error() { log "$RED" "❌ $1"; }

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --skip-tests) SKIP_TESTS=true; shift ;;
      --skip-test-data) SKIP_TEST_DATA=true; shift ;;
      --detach) DETACH=true; shift ;;
      --accept-default-admin) ACCEPT_DEFAULT_ADMIN=true; shift ;;
      --help) print_usage; exit 0 ;;
      *) log_error "Unknown option: $1"; print_usage; exit 1 ;;
    esac
  done
}

load_env_file() {
  if [[ -f "$ENV_FILE" ]]; then
    log_info "Loading environment overrides from $ENV_FILE"
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  else
    if [[ -f "$ENV_TEMPLATE" ]]; then
      log_warn "Environment file $ENV_FILE not found. Copy $ENV_TEMPLATE and customize credentials."
    else
      log_warn "Environment file $ENV_FILE not found. Create it to persist credentials across runs."
    fi
  fi
}

setup_directories() {
  mkdir -p "$LOG_DIR" "$PID_DIR"
}

check_prerequisites() {
  log_info "Checking prerequisites..."

  command -v docker >/dev/null || { log_error "Docker is required."; exit 1; }
  docker compose version >/dev/null 2>&1 || { log_error "Docker Compose V2 plugin is required."; exit 1; }
  command -v java >/dev/null || { log_error "Java 21 is required."; exit 1; }
  local java_version
  java_version="$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)"
  if [[ "$java_version" -lt 21 ]]; then
    log_error "Java 21+ required (found $java_version)."; exit 1
  fi
  command -v git >/dev/null || { log_error "Git is required."; exit 1; }
  if ! command -v npm >/dev/null; then
    log_warn "npm not found – frontend launch will be skipped."
  fi

  if [[ ! -f "$COMPOSE_FILE" ]]; then
    log_error "compose.yml not found at $COMPOSE_FILE"; exit 1
  fi
  log_success "Prerequisites satisfied."
}

prompt_keycloak_password() {
  if [[ ${KEYCLOAK_ADMIN_PASSWORD:-} ]]; then
    log_info "Using KEYCLOAK_ADMIN_PASSWORD from environment."
    return
  fi
  if $ACCEPT_DEFAULT_ADMIN; then
    export KEYCLOAK_ADMIN_PASSWORD=admin
    log_warn "Using default Keycloak admin credentials; rotate manually ASAP."
    return
  fi

  log_info "Keycloak admin password not set. Please choose a secure password."
  while true; do
    read -rs -p "Enter Keycloak admin password: " pass1; echo
    read -rs -p "Confirm password: " pass2; echo
    if [[ -z "$pass1" ]]; then
      log_warn "Password cannot be empty."
    elif [[ "$pass1" != "$pass2" ]]; then
      log_warn "Passwords do not match."
    else
      export KEYCLOAK_ADMIN_PASSWORD="$pass1"
      log_success "Keycloak admin password captured for this session."
      break
    fi
  done
}

start_infrastructure() {
  log_info "Starting infrastructure services..."
  docker compose -f "$COMPOSE_FILE" up -d postgres keycloak redis prometheus grafana >/dev/null
  log_success "Docker services started."
}

wait_with_timeout() {
  local cmd="$1"
  local interval="$2"
  local max_wait="$3"
  local elapsed=0

  while ! eval "$cmd" >/dev/null 2>&1; do
    sleep "$interval"
    elapsed=$((elapsed + interval))
    if (( elapsed >= max_wait )); then
      return 1
    fi
  done
  return 0
}

wait_for_services() {
  log_info "Waiting for PostgreSQL..."
  if wait_with_timeout "docker exec eaf-postgres pg_isready -U eaf" 3 90; then
    log_success "PostgreSQL ready."
  else
    log_error "PostgreSQL failed to become ready within 90 seconds."
    exit 1
  fi

  log_info "Waiting for Keycloak..."
  if wait_with_timeout "curl -sf http://localhost:8180/realms/eaf-test/.well-known/openid-configuration" 5 300; then
    log_success "Keycloak ready."
  else
    log_error "Keycloak health endpoint not ready within 300 seconds."
    exit 1
  fi

  log_info "Waiting for Redis..."
  if wait_with_timeout "docker exec eaf-redis redis-cli ping | grep -q PONG" 2 60; then
    log_success "Redis ready."
  else
    log_error "Redis ping failed within 60 seconds."
    exit 1
  fi
}

run_gradle_task_if_present() {
  local task="$1"
  if ./gradlew -q tasks --all | grep -Fq " $task"; then
    log_info "Running ./gradlew $task"
    ./gradlew "$task"
  else
    log_warn "Gradle task $task not found – skipping."
  fi
}

load_event_store_schema() {
  if [[ -f "$PROJECT_ROOT/scripts/sql/event-store-schema.sql" ]]; then
    log_info "Applying event store schema..."
    docker exec -i eaf-postgres psql -U eaf -d eaf < "$PROJECT_ROOT/scripts/sql/event-store-schema.sql"
    log_success "Event store schema applied."
  fi
  if ! $SKIP_TEST_DATA && [[ -f "$PROJECT_ROOT/scripts/sql/test-data.sql" ]]; then
    log_info "Seeding test data..."
    docker exec -i eaf-postgres psql -U eaf -d eaf < "$PROJECT_ROOT/scripts/sql/test-data.sql"
    log_success "Test data loaded."
  else
    log_info "Skipping test data seeding."
  fi
}

configure_keycloak() {
  if [[ ! -f "$KEYCLOAK_REALM_FILE" ]]; then
    log_warn "Keycloak realm file not found at $KEYCLOAK_REALM_FILE"
    return
  fi
  log_info "Configuring Keycloak realm and verifying credentials..."
  KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-}" KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}" \
    "$PROJECT_ROOT/scripts/keycloak-setup.sh"
  log_success "Keycloak realm confirmed."
}

run_quality_checks() {
  log_info "Running quality gates (ktlint, detekt, konsist, pitest, coverage)..."
  if $SKIP_TESTS; then
    ./gradlew clean check -x test -x integrationTest
  else
    ./gradlew clean check
  fi
  log_success "Gradle quality gates complete."
}

run_tests_explicit() {
  if $SKIP_TESTS; then
    log_warn "Skipping Constitutional TDD execution (--skip-tests)."
    return
  fi
  log_info "Executing fast tests (test) and integration tests separately for runtime metrics..."
  local tests_start tests_end
  tests_start=$(date +%s)
  ./gradlew test integrationTest
  tests_end=$(date +%s)
  log_success "Fast + integration tests passed in $((tests_end - tests_start))s."
}

start_backend() {
  log_info "Starting licensing-server (bootRun)..."
  SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev,debug}" \
  SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/eaf}" \
  SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-eaf}" \
  SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-eaf}" \
    ./gradlew :products:licensing-server:bootRun > "$LOG_DIR/backend.log" 2>&1 &
  echo $! > "$PID_DIR/backend.pid"
  log_info "Starting backend health wait..."
  if wait_with_timeout "curl -sf http://localhost:8080/actuator/health" 3 120; then
    log_success "Licensing server running (PID $(cat "$PID_DIR/backend.pid"))."
  else
    log_error "Licensing server failed to report healthy within 120 seconds."
    exit 1
  fi
}

start_frontend() {
  if ! command -v npm >/dev/null; then
    log_warn "Frontend skipped (npm unavailable)."
    return
  fi
  log_info "Starting admin frontend..."
  (cd "$PROJECT_ROOT/apps/admin" && npm install --silent && npm run dev) > "$LOG_DIR/frontend.log" 2>&1 &
  echo $! > "$PID_DIR/frontend.pid"
  if wait_with_timeout "curl -sf http://localhost:3000" 3 90; then
    log_success "Admin frontend running (PID $(cat "$PID_DIR/frontend.pid"))."
  else
    log_error "Admin frontend failed to start within 90 seconds."
    exit 1
  fi
}

print_summary() {
  local end_time
  end_time=$(date +%s)
  local duration=$((end_time - START_TIME))
  local grafana_port="${GRAFANA_PORT:-3001}"

  cat <<SUMMARY

================================================================
✅ EAF Development Environment Ready in ${duration}s
================================================================
Services:
  • Backend API:          http://localhost:8080
  • Admin Portal:         http://localhost:3000
  • Keycloak:             http://localhost:8180 (admin: ${KEYCLOAK_ADMIN:-admin})
  • PostgreSQL:           localhost:5432 (user/password: eaf / eaf)
  • Redis:                localhost:6379
  • Grafana:              http://localhost:${grafana_port} (admin / grafana)
  • Prometheus:           http://localhost:9090

Security reminder: Keycloak admin password persisted for this session. Store safely.
Use ./scripts/stop-dev.sh or Ctrl+C to stop services.
SUMMARY
}

cleanup() {
  log_info "Cleaning up background processes..."
  if [[ -f "$PID_DIR/backend.pid" ]]; then
    kill "$(cat "$PID_DIR/backend.pid")" 2>/dev/null || true
    rm "$PID_DIR/backend.pid"
  fi
  if [[ -f "$PID_DIR/frontend.pid" ]]; then
    kill "$(cat "$PID_DIR/frontend.pid")" 2>/dev/null || true
    rm "$PID_DIR/frontend.pid"
  fi
}

trap_cleanup() {
  if ! $DETACH; then
    trap cleanup EXIT
  fi
}

install_pre_commit_hooks() {
  log_info "Installing pre-commit hooks (Story 8.2)..."
  if ./gradlew installGitHooks --quiet 2>/dev/null; then
    log_success "Pre-commit hooks installed (<30s validation target)"
    log_info "  Bypass for emergencies: git commit --no-verify"
  else
    log_warn "Pre-commit hooks installation failed - continuing without hooks"
  fi
}

main() {
  parse_args "$@"
  load_env_file
  setup_directories
  check_prerequisites
  prompt_keycloak_password
  trap_cleanup
  log_info "Resetting development environment to ensure a clean start..."
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1

  start_infrastructure
  wait_for_services
  load_event_store_schema
  log_info "Waiting for Keycloak Admin CLI to be ready..."
  local kcadm_cmd="docker exec eaf-keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user \"${KEYCLOAK_ADMIN:-admin}\" --password \"${KEYCLOAK_ADMIN_PASSWORD}\""
  if wait_with_timeout "$kcadm_cmd" 5 180; then
    log_success "Keycloak Admin CLI is ready."
  else
    log_error "Keycloak Admin CLI failed to authenticate within 180 seconds. Check Keycloak logs."
    exit 1
  fi
  configure_keycloak

  # Story 8.2: Install pre-commit hooks (AC3)
  install_pre_commit_hooks

  run_quality_checks
  run_tests_explicit
  start_backend
  start_frontend
  print_summary

  if $DETACH; then
    log_info "Detached mode active – stack will continue running. Use ./scripts/stop-dev.sh when finished."
  else
    log_info "Press Ctrl+C to stop services..."
    wait
  fi
}

main "$@"
