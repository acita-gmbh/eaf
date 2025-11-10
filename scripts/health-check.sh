#!/bin/bash
set -e

# EAF v1.0 Service Health Check
# ==============================
# Story 1.6: One-Command Initialization Script
# Validates all services are ready before proceeding

# Configuration
MAX_WAIT_SECONDS=120  # 2 minutes timeout (Constraint C3)
CHECK_INTERVAL=5      # Check every 5 seconds
ELAPSED=0

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_success() {
    echo -e "  ${GREEN}✅${NC} $1"
}

print_error() {
    echo -e "  ${RED}❌${NC} $1"
}

print_waiting() {
    echo -e "  ${YELLOW}⏱️${NC} $1"
}

# Check if PostgreSQL is ready
check_postgres() {
    local POSTGRES_USER=${POSTGRES_USER:-eaf_user}
    local POSTGRES_DB=${POSTGRES_DB:-eaf}

    if docker exec eaf-postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" &> /dev/null; then
        return 0
    else
        return 1
    fi
}

# Check if Keycloak is ready (realm loaded)
check_keycloak() {
    # Check from host: Verify 'eaf' realm is accessible
    # Note: curl/wget not available in Keycloak container, checking from host instead
    if curl -s -f http://localhost:8080/realms/eaf &> /dev/null; then
        return 0
    else
        return 1
    fi
}

# Check if Redis is ready
check_redis() {
    if docker exec eaf-redis redis-cli ping &> /dev/null; then
        return 0
    else
        return 1
    fi
}

# Check if Prometheus is ready
check_prometheus() {
    # Check from host (wget not available in Prometheus container)
    if curl -s -f http://localhost:9090/-/ready &> /dev/null; then
        return 0
    else
        return 1
    fi
}

# Check if Grafana is ready
check_grafana() {
    # Check from host (curl not available in Grafana container)
    if curl -s -f http://localhost:3100/api/health &> /dev/null; then
        return 0
    else
        return 1
    fi
}

# Main health check loop
main() {
    echo "Health Check - Waiting for all services to be ready..."
    echo "Maximum wait time: ${MAX_WAIT_SECONDS}s"
    echo ""

    # Service status tracking
    POSTGRES_READY=false
    REDIS_READY=false
    KEYCLOAK_READY=false
    PROMETHEUS_READY=false
    GRAFANA_READY=false

    while [ $ELAPSED -lt $MAX_WAIT_SECONDS ]; do
        ALL_READY=true

        # Check PostgreSQL
        if [ "$POSTGRES_READY" = false ]; then
            if check_postgres; then
                POSTGRES_READY=true
                print_success "PostgreSQL is ready"
            else
                ALL_READY=false
            fi
        fi

        # Check Redis
        if [ "$REDIS_READY" = false ]; then
            if check_redis; then
                REDIS_READY=true
                print_success "Redis is ready"
            else
                ALL_READY=false
            fi
        fi

        # Check Keycloak
        if [ "$KEYCLOAK_READY" = false ]; then
            if check_keycloak; then
                KEYCLOAK_READY=true
                print_success "Keycloak is ready (realm 'eaf' loaded)"
            else
                ALL_READY=false
            fi
        fi

        # Check Prometheus
        if [ "$PROMETHEUS_READY" = false ]; then
            if check_prometheus; then
                PROMETHEUS_READY=true
                print_success "Prometheus is ready"
            else
                ALL_READY=false
            fi
        fi

        # Check Grafana
        if [ "$GRAFANA_READY" = false ]; then
            if check_grafana; then
                GRAFANA_READY=true
                print_success "Grafana is ready"
            else
                ALL_READY=false
            fi
        fi

        # If all services are ready, exit successfully
        if [ "$ALL_READY" = true ]; then
            echo ""
            echo "All services are healthy! 🎉"
            exit 0
        fi

        # Wait before next check
        if [ $ELAPSED -lt $MAX_WAIT_SECONDS ]; then
            print_waiting "Waiting ${CHECK_INTERVAL}s before next check... (${ELAPSED}s elapsed)"
            sleep $CHECK_INTERVAL
            ELAPSED=$((ELAPSED + CHECK_INTERVAL))
        fi
    done

    # Timeout reached
    echo ""
    print_error "Health check timeout reached after ${MAX_WAIT_SECONDS}s"
    echo ""
    echo "Service Status:"
    [ "$POSTGRES_READY" = true ] && echo "  ✅ PostgreSQL" || echo "  ❌ PostgreSQL"
    [ "$REDIS_READY" = true ] && echo "  ✅ Redis" || echo "  ❌ Redis"
    [ "$KEYCLOAK_READY" = true ] && echo "  ✅ Keycloak" || echo "  ❌ Keycloak"
    [ "$PROMETHEUS_READY" = true ] && echo "  ✅ Prometheus" || echo "  ❌ Prometheus"
    [ "$GRAFANA_READY" = true ] && echo "  ✅ Grafana" || echo "  ❌ Grafana"
    echo ""
    echo "Troubleshooting:"
    echo "  - Check service logs:  docker compose logs <service-name>"
    echo "  - Restart services:    docker compose restart"
    echo "  - View all logs:       docker compose logs -f"
    exit 1
}

# Execute main function
main "$@"
