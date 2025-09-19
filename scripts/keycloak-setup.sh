#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_URL="${KEYCLOAK_SERVER_URL:-http://127.0.0.1:8080}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM_FILE="${SCRIPT_DIR}/../shared/testing/src/main/resources/test-realm.json"
CONTAINER_NAME="${KEYCLOAK_CONTAINER_NAME:-eaf-keycloak}"
REALM_NAME="${KEYCLOAK_REALM_NAME:-eaf-test}"

if [[ -z "$ADMIN_PASSWORD" ]]; then
  echo "KEYCLOAK_ADMIN_PASSWORD must be provided" >&2
  exit 1
fi

if ! docker exec "$CONTAINER_NAME" test -x /opt/keycloak/bin/kcadm.sh >/dev/null 2>&1; then
  echo "kcadm.sh not available in container $CONTAINER_NAME" >&2
  exit 1
fi

if ! docker exec "$CONTAINER_NAME" /opt/keycloak/bin/kcadm.sh config credentials \
  --server "$SERVER_URL" --realm master --user "$ADMIN_USER" --password "$ADMIN_PASSWORD" >/dev/null 2>&1; then
  echo "Failed to authenticate with Keycloak admin CLI. Check credentials." >&2
  exit 1
fi

echo "Authenticated with Keycloak admin CLI."

if [[ -f "$REALM_FILE" ]]; then
  if docker exec "$CONTAINER_NAME" /opt/keycloak/bin/kcadm.sh get "realms/${REALM_NAME}" >/dev/null 2>&1; then
    echo "Realm ${REALM_NAME} already present."
  else
    docker exec "$CONTAINER_NAME" /opt/keycloak/bin/kcadm.sh create realms -f /opt/keycloak/data/import/test-realm.json >/dev/null
    echo "Imported realm ${REALM_NAME} into Keycloak."
  fi
else
  echo "Realm file not found at $REALM_FILE" >&2
fi
