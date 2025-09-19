#!/usr/bin/env bash
set -euo pipefail

BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

msg() {
  printf "%b%s%b\n" "$BLUE" "$1" "$NC"
}

msg "🚀 Enterprise Application Framework - One-Command Setup"
msg "Checking prerequisites..."

command -v docker >/dev/null && command -v docker compose >/dev/null || {
  printf "%bDocker and docker compose are required.%b\n" "$YELLOW" "$NC" >&2
  exit 1
}

command -v java >/dev/null || {
  printf "%bJava 21 is required.%b\n" "$YELLOW" "$NC" >&2
  exit 1
}

msg "Installing Gradle wrappers and bootstrapping modules..."
./gradlew --version >/dev/null

msg "Starting infrastructure containers..."
docker compose -f docker-compose.dev.yml up -d postgres keycloak redis >/dev/null

msg "Running database migrations..."
./gradlew flywayMigrate >/dev/null

msg "Building project..."
./gradlew clean build

msg "Setup complete."
