#!/bin/bash
set -e

# EAF v1.0 Seed Data Script
# ==========================
# Story 1.6: One-Command Initialization Script
# Validates test data is loaded (idempotent operation)

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

print_step() {
    echo -e "  ${BLUE}▶${NC} $1"
}

print_success() {
    echo -e "  ${GREEN}✅${NC} $1"
}

print_error() {
    echo -e "  ${RED}❌${NC} $1"
}

print_info() {
    echo -e "  ${BLUE}ℹ️${NC} $1"
}

# Verify Keycloak test users exist (Constraint C9: idempotent)
verify_keycloak_users() {
    print_step "Verifying Keycloak test users..."

    # Expected test users from realm-export.json
    local EXPECTED_USERS=("admin" "viewer" "tenant-b-admin")
    local ALL_USERS_EXIST=true

    for USERNAME in "${EXPECTED_USERS[@]}"; do
        # Query Keycloak admin API to check if user exists
        local USER_EXISTS
        USER_EXISTS=$(docker exec eaf-keycloak /opt/keycloak/bin/kcadm.sh get users \
            -r eaf \
            --server http://localhost:8080 \
            --realm master \
            --user admin \
            --password admin \
            --query username="$USERNAME" \
            2>/dev/null | grep -c "\"username\" : \"$USERNAME\"" || echo "0")

        if [ "$USER_EXISTS" -eq "0" ]; then
            print_error "User '$USERNAME' not found in realm 'eaf'"
            ALL_USERS_EXIST=false
        else
            print_success "User '$USERNAME' exists"
        fi
    done

    if [ "$ALL_USERS_EXIST" = false ]; then
        print_error "Some test users are missing"
        echo ""
        echo "Expected users: admin, viewer, tenant-b-admin"
        echo "These users should be imported from docker/keycloak/realm-export.json"
        return 1
    fi

    return 0
}

# Verify PostgreSQL schema is initialized
verify_postgres_schema() {
    print_step "Verifying PostgreSQL schema..."

    local POSTGRES_USER=${POSTGRES_USER:-eaf_user}
    local POSTGRES_DB=${POSTGRES_DB:-eaf}

    # Check if eaf schema exists
    local SCHEMA_EXISTS
    SCHEMA_EXISTS=$(docker exec eaf-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
        "SELECT 1 FROM information_schema.schemata WHERE schema_name='eaf'" 2>/dev/null || echo "0")

    if [ "$SCHEMA_EXISTS" = "1" ]; then
        print_success "Schema 'eaf' exists"

        # List Axon Framework tables
        local TABLE_COUNT
        TABLE_COUNT=$(docker exec eaf-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='eaf'" 2>/dev/null || echo "0")

        print_info "Found $TABLE_COUNT tables in schema 'eaf'"
    else
        print_error "Schema 'eaf' not found"
        echo ""
        echo "Schema should be created by docker/postgres/init-scripts/01-init.sql"
        return 1
    fi

    return 0
}

# Verify Redis is responding
verify_redis() {
    print_step "Verifying Redis..."

    if docker exec eaf-redis redis-cli ping &> /dev/null; then
        print_success "Redis is responding"

        # Get Redis info
        local REDIS_VERSION
        REDIS_VERSION=$(docker exec eaf-redis redis-cli INFO server 2>/dev/null | grep "redis_version:" | cut -d: -f2 | tr -d '\r')
        if [ -n "$REDIS_VERSION" ]; then
            print_info "Redis version: $REDIS_VERSION"
        fi
    else
        print_error "Redis is not responding"
        return 1
    fi

    return 0
}

# Main seed data validation
main() {
    echo "Seed Data Validation"
    echo "===================="
    echo ""
    print_info "Validating test data and service configurations..."
    echo ""

    # Track overall success
    local VALIDATION_FAILED=false

    # Verify PostgreSQL schema
    if ! verify_postgres_schema; then
        VALIDATION_FAILED=true
    fi
    echo ""

    # Verify Keycloak test users
    if ! verify_keycloak_users; then
        VALIDATION_FAILED=true
    fi
    echo ""

    # Verify Redis
    if ! verify_redis; then
        VALIDATION_FAILED=true
    fi
    echo ""

    # Final status
    if [ "$VALIDATION_FAILED" = true ]; then
        print_error "Seed data validation failed"
        echo ""
        echo "Troubleshooting:"
        echo "  - Check PostgreSQL init scripts: docker/postgres/init-scripts/"
        echo "  - Verify Keycloak realm import: docker/keycloak/realm-export.json"
        echo "  - View service logs: docker compose logs <service-name>"
        exit 1
    else
        print_success "All seed data validated successfully"
        echo ""
        echo "📋 Test Data Summary:"
        echo "  - PostgreSQL: Schema 'eaf' with Axon Framework tables"
        echo "  - Keycloak: Realm 'eaf' with 3 test users (admin, viewer, tenant-b-admin)"
        echo "  - Redis: Responding to commands"
        echo ""
        echo "🔑 Test User Credentials:"
        echo "  - admin@eaf.local / password (tenant: tenant-a, roles: admin, user)"
        echo "  - viewer@eaf.local / password (tenant: tenant-a, roles: user)"
        echo "  - tenant-b-admin@eaf.local / password (tenant: tenant-b, roles: admin, user)"
        exit 0
    fi
}

# Execute main function
main "$@"
