#!/bin/bash
# Flowable Schema Restore
# WARNING: This will DROP the existing Flowable schema!
#
# Story 6.4 (Subtask 2.4) - Operational Procedures

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BACKUP_FILE="$1"

if [ -z "$BACKUP_FILE" ]; then
    echo -e "${RED}Usage: $0 <backup_file.dump>${NC}"
    echo ""
    echo "Example:"
    echo "  $0 ./backups/flowable/flowable_schema_20251001_143000.dump"
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    echo -e "${RED}❌ Backup file not found: $BACKUP_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}=== Flowable Schema Restore ===${NC}"
echo "Backup file: $BACKUP_FILE"
echo ""
echo -e "${RED}⚠️  WARNING: This will DROP the existing Flowable schema!${NC}"
echo ""
read -p "Are you sure you want to continue? (yes/no): " -r
if [[ ! $REPLY =~ ^yes$ ]]; then
    echo "Restore cancelled."
    exit 0
fi

# 1. Verify backup file integrity
echo -e "${YELLOW}Verifying backup file...${NC}"
pg_restore --list "$BACKUP_FILE" > /dev/null
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Backup file is corrupt or invalid${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Backup file verified${NC}"

# 2. Stop application (manual step - operator must stop app)
echo ""
echo -e "${YELLOW}⏸️  STEP 1: Stop the application before proceeding${NC}"
echo "   Press ENTER when the application is stopped..."
read -r

# 3. Drop current Flowable schema
echo -e "${YELLOW}Dropping current Flowable schema...${NC}"
psql -h "${POSTGRES_HOST:-localhost}" \
     -U "${POSTGRES_USER:-postgres}" \
     -d "${POSTGRES_DB:-eaf_database}" \
     -c "DROP SCHEMA IF EXISTS flowable CASCADE;"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Current schema dropped${NC}"
else
    echo -e "${RED}❌ Failed to drop schema${NC}"
    exit 1
fi

# 4. Restore from backup
echo -e "${YELLOW}Restoring from backup...${NC}"
pg_restore -h "${POSTGRES_HOST:-localhost}" \
           -U "${POSTGRES_USER:-postgres}" \
           -d "${POSTGRES_DB:-eaf_database}" \
           --schema=flowable \
           "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Flowable schema restored from: $BACKUP_FILE${NC}"
    echo ""
    echo -e "${GREEN}=== Restore Complete ===${NC}"
    echo ""
    echo -e "${YELLOW}🚀 NEXT STEPS:${NC}"
    echo "   1. Verify schema integrity in PostgreSQL"
    echo "   2. Start the application"
    echo "   3. Run smoke tests to validate Flowable engine"
    echo "   4. Monitor metrics for process errors"
else
    echo -e "${RED}❌ Restore failed!${NC}"
    echo ""
    echo -e "${RED}CRITICAL: Database may be in inconsistent state${NC}"
    echo "Manual intervention required."
    exit 1
fi
