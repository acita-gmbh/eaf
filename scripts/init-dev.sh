#!/bin/bash
set -e

# EAF v1.0 Development Environment Initialization
# ================================================
# Story 1.6: One-Command Initialization Script
# This script orchestrates the complete development environment setup

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored messages
print_step() {
    echo -e "${BLUE}▶${NC} $1"
}

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

# Function to check if Docker is running
check_docker() {
    print_step "Checking Docker installation..."

    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker and try again."
        echo "Visit: https://docs.docker.com/get-docker/"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        print_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi

    print_success "Docker is installed and running"
}

# Function to check if Docker Compose is available
check_docker_compose() {
    print_step "Checking Docker Compose installation..."

    if ! docker compose version &> /dev/null; then
        print_error "Docker Compose is not available. Please install Docker Compose v2+ and try again."
        echo "Visit: https://docs.docker.com/compose/install/"
        exit 1
    fi

    print_success "Docker Compose is available"
}

# Main initialization
main() {
    echo ""
    echo "🚀 EAF v1.0 Development Environment Initialization"
    echo "================================================"
    echo ""

    # Record start time for performance tracking
    START_TIME=$(date +%s)

    # Step 1: Validate prerequisites
    check_docker
    check_docker_compose
    echo ""

    # Step 2: Start Docker Compose stack
    print_step "Starting Docker services..."
    if docker compose up -d; then
        print_success "Docker services started"
    else
        print_error "Failed to start Docker services"
        exit 1
    fi
    echo ""

    # Step 3: Wait for services to be healthy
    print_step "Waiting for services to be ready..."
    if ./scripts/health-check.sh; then
        print_success "All services are healthy"
    else
        print_error "Service health check failed"
        print_warning "Run 'docker compose logs' to see service logs"
        exit 1
    fi
    echo ""

    # Step 4: Load seed data
    print_step "Loading test data..."
    if ./scripts/seed-data.sh; then
        print_success "Test data loaded"
    else
        print_error "Failed to load test data"
        exit 1
    fi
    echo ""

    # Step 5: Install Git hooks
    print_step "Installing Git hooks..."
    if ./scripts/install-git-hooks.sh; then
        print_success "Git hooks installed"
    else
        print_warning "Git hooks installation had warnings (check output above)"
    fi
    echo ""

    # Step 6: Download Gradle dependencies
    print_step "Downloading Gradle dependencies..."
    if ./gradlew dependencies --quiet; then
        print_success "Gradle dependencies downloaded"
    else
        print_error "Failed to download Gradle dependencies"
        exit 1
    fi
    echo ""

    # Calculate total execution time
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    MINUTES=$((DURATION / 60))
    SECONDS=$((DURATION % 60))

    # Final success message
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    print_success "Development environment ready!"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "⏱️  Total initialization time: ${MINUTES}m ${SECONDS}s"
    echo ""
    echo "📋 Services:"
    echo "  - PostgreSQL:  localhost:5432"
    echo "  - Keycloak:    http://localhost:8080"
    echo "  - Redis:       localhost:6379"
    echo "  - Prometheus:  http://localhost:9090"
    echo "  - Grafana:     http://localhost:3100"
    echo ""
    echo "🔑 Default Credentials:"
    echo "  - PostgreSQL:  user=eaf_user, password=eaf_password, database=eaf"
    echo "  - Keycloak:    admin/admin (realm: eaf)"
    echo "  - Grafana:     admin/admin"
    echo ""
    echo "▶️  Next Steps:"
    echo "  - Start backend:  ./gradlew :products:widget-demo:bootRun"
    echo "  - Run tests:      ./gradlew test"
    echo "  - Stop services:  docker compose down"
    echo ""

    # Performance target validation (AC6: <5 minutes)
    if [ $DURATION -gt 300 ]; then
        print_warning "Initialization took longer than 5 minutes target"
        echo "   Consider optimizing Docker image pulls or Gradle dependency downloads"
    fi
}

# Execute main function
main "$@"
