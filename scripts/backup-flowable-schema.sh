#!/bin/bash
# Flowable Schema Backup for PostgreSQL
# Execute BEFORE any Flowable version upgrade or schema migration
#
# Story 6.4 (Subtask 2.3) - Operational Procedures

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BACKUP_DIR="./backups/flowable"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/flowable_schema_${TIMESTAMP}.dump"

echo -e "${GREEN}=== Flowable Schema Backup ===${NC}"
echo "Timestamp: ${TIMESTAMP}"

# Create backup directory if it doesn't exist
mkdir -p "$BACKUP_DIR"

# Backup Flowable schema only (not entire database)
echo -e "${YELLOW}Backing up Flowable schema...${NC}"
pg_dump -h "${POSTGRES_HOST:-localhost}" \
        -U "${POSTGRES_USER:-postgres}" \
        -d "${POSTGRES_DB:-eaf_database}" \
        --schema=flowable \
        --format=custom \
        --file="$BACKUP_FILE"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Flowable schema backed up: $BACKUP_FILE${NC}"

    # Verify backup integrity
    echo -e "${YELLOW}Verifying backup integrity...${NC}"
    pg_restore --list "$BACKUP_FILE" > /dev/null

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Backup verified successfully${NC}"

        # Display backup size
        BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
        echo "Backup size: ${BACKUP_SIZE}"
    else
        echo -e "${RED}❌ Backup verification failed!${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ Backup failed!${NC}"
    exit 1
fi

# Cleanup old backups (keep last 7 days)
echo -e "${YELLOW}Cleaning up old backups...${NC}"
find "$BACKUP_DIR" -name "flowable_schema_*.dump" -mtime +7 -delete
echo -e "${GREEN}🗑️  Cleaned up backups older than 7 days${NC}"

echo -e "${GREEN}=== Backup Complete ===${NC}"
echo "Backup location: $BACKUP_FILE"
echo ""
echo "To restore this backup, run:"
echo "  ./scripts/restore-flowable-schema.sh $BACKUP_FILE"
