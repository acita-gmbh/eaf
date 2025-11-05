#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'HELP'
Usage: create-event-store-partition.sh [options]

Creates a monthly partition for the Axon DomainEventEntry table.

Options:
  --month YYYY-MM      Target month to create (default: next month)
  --host HOST          PostgreSQL host (default: localhost)
  --port PORT          PostgreSQL port (default: 5432)
  --user USER          PostgreSQL user (default: eaf_user)
  --dbname NAME        PostgreSQL database (default: eaf)
  --schema SCHEMA      Schema containing the event store tables (default: public)
  --table NAME         Partitioned table name (default: domainevententry)
  --help               Show this help message

Environment overrides:
  DB_HOST, DB_PORT, DB_USER, DB_NAME, DB_SCHEMA, EVENT_TABLE

Examples:
  ./scripts/create-event-store-partition.sh
  ./scripts/create-event-store-partition.sh --month 2025-12 --host localhost --user postgres
HELP
}

HOST="${DB_HOST:-localhost}"
PORT="${DB_PORT:-5432}"
USER="${DB_USER:-eaf_user}"
DB="${DB_NAME:-eaf}"
SCHEMA="${DB_SCHEMA:-public}"
TABLE="${EVENT_TABLE:-domainevententry}"
MONTH_SPEC=""

validate_identifier() {
    local identifier="$1"
    if [[ ! "$identifier" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
        echo "Invalid identifier: $identifier" >&2
        exit 1
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --month)
            MONTH_SPEC="$2"
            shift 2
            ;;
        --host)
            HOST="$2"
            shift 2
            ;;
        --port)
            PORT="$2"
            shift 2
            ;;
        --user)
            USER="$2"
            shift 2
            ;;
        --dbname)
            DB="$2"
            shift 2
            ;;
        --schema)
            SCHEMA="$2"
            shift 2
            ;;
        --table)
            TABLE="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage
            exit 1
            ;;
    esac
done

validate_identifier "$SCHEMA"
validate_identifier "$TABLE"

if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 is required to compute partition boundaries" >&2
    exit 1
fi

read -r PARTITION_NAME START_DATE END_DATE <<EOF
$(python3 - <<'PY' "$MONTH_SPEC" "$TABLE"
from datetime import datetime, timezone
import sys

month_spec = sys.argv[1]
table_name = sys.argv[2]
now = datetime.now(timezone.utc)

if month_spec:
    try:
        start = datetime.strptime(month_spec + "-01", "%Y-%m-%d").replace(tzinfo=timezone.utc)
    except ValueError as exc:
        raise SystemExit(f"Invalid --month value '{month_spec}': {exc}") from exc
else:
    # Default to next calendar month from current UTC date
    year = now.year
    month = now.month + 1
    if month == 13:
        month = 1
        year += 1
    start = datetime(year, month, 1, tzinfo=timezone.utc)

end_year = start.year
end_month = start.month + 1
if end_month == 13:
    end_month = 1
    end_year += 1
end = datetime(end_year, end_month, 1, tzinfo=timezone.utc)

partition_suffix = start.strftime("%Y_%m")

print(f"{table_name}_{partition_suffix} {start.strftime('%Y-%m-%d')}T00:00:00Z {end.strftime('%Y-%m-%d')}T00:00:00Z")
PY)
EOF

# Validate partition name follows expected pattern: {TABLE}_{YYYY_MM}
if [[ ! "$PARTITION_NAME" =~ ^${TABLE}_[0-9]{4}_[0-9]{2}$ ]]; then
    echo "Invalid PARTITION_NAME format: $PARTITION_NAME" >&2
    exit 1
fi

# Validate START_DATE follows strict ISO 8601 format
if [[ ! "$START_DATE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T00:00:00Z$ ]]; then
    echo "Invalid START_DATE format: $START_DATE" >&2
    exit 1
fi

# Validate END_DATE follows strict ISO 8601 format
if [[ ! "$END_DATE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T00:00:00Z$ ]]; then
    echo "Invalid END_DATE format: $END_DATE" >&2
    exit 1
fi

PSQL_OPTS=(
    "--host=$HOST"
    "--port=$PORT"
    "--username=$USER"
    "--dbname=$DB"
    "--set" "ON_ERROR_STOP=on"
    "--no-psqlrc"
)

echo "Creating partition ${SCHEMA}.${PARTITION_NAME} for range ${START_DATE} -> ${END_DATE}"

# Use PostgreSQL format() for proper identifier/literal quoting
psql "${PSQL_OPTS[@]}" \
    -v schema="$SCHEMA" \
    -v partition_name="$PARTITION_NAME" \
    -v table_name="$TABLE" \
    -v start_date="$START_DATE" \
    -v end_date="$END_DATE" \
    <<'SQL'
SELECT format(
    'CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %I.%I FOR VALUES FROM (%L) TO (%L);',
    :'schema', :'partition_name', :'schema', :'table_name', :'start_date', :'end_date'
);
\gexec
SQL
echo "Partition ensured successfully."
