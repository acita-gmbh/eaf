# Development Workflow

## Overview

The EAF development workflow provides a streamlined, developer-friendly experience with one-command onboarding, automated quality gates, and comprehensive scaffolding tools. The workflow supports rapid development while maintaining enterprise-grade quality and security standards.

## One-Command Onboarding

### Complete Environment Setup

```bash
#!/bin/bash
# scripts/init-dev.sh - Complete development environment setup

set -euo pipefail

echo "🚀 Enterprise Application Framework - One-Command Setup"
echo "======================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check Docker
    if ! command -v docker >/dev/null 2>&1; then
        log_error "Docker is required but not installed"
        echo "Please install Docker: https://docs.docker.com/get-docker/"
        exit 1
    fi

    # Check Docker Compose
    if ! docker compose version >/dev/null 2>&1; then
        log_error "Docker Compose is required but not available"
        echo "Please ensure Docker Compose is installed and available"
        exit 1
    fi

    # Check Java 21
    if ! command -v java >/dev/null 2>&1; then
        log_error "Java 21 is required but not installed"
        echo "Please install Java 21: https://adoptium.net/"
        exit 1
    fi

    local java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt 21 ]; then
        log_error "Java 21 or higher is required, found Java $java_version"
        exit 1
    fi

    # Check Node.js (for frontend development)
    if ! command -v npm >/dev/null 2>&1; then
        log_warning "Node.js/npm not found - frontend development will not be available"
    fi

    # Check Git
    if ! command -v git >/dev/null 2>&1; then
        log_error "Git is required but not installed"
        exit 1
    fi

    log_success "All prerequisites satisfied"
}

# Start infrastructure services
start_infrastructure() {
    log_info "Starting infrastructure services..."

    # Create docker network if it doesn't exist
    docker network create eaf-network 2>/dev/null || true

    # Start infrastructure with health checks
    docker compose -f docker-compose.dev.yml up -d \
        postgres keycloak redis prometheus grafana

    log_info "Waiting for services to be healthy..."

    # Wait for PostgreSQL
    log_info "Waiting for PostgreSQL..."
    timeout 60 bash -c 'until docker exec eaf-postgres pg_isready -U eaf; do sleep 2; done'
    log_success "PostgreSQL is ready"

    # Wait for Keycloak
    log_info "Waiting for Keycloak..."
    timeout 120 bash -c 'until curl -sf http://localhost:8180/health/ready >/dev/null; do sleep 5; done'
    log_success "Keycloak is ready"

    # Wait for Redis
    log_info "Waiting for Redis..."
    timeout 30 bash -c 'until docker exec eaf-redis redis-cli ping | grep PONG >/dev/null; do sleep 2; done'
    log_success "Redis is ready"

    log_success "All infrastructure services are running"
}

# Initialize database
initialize_database() {
    log_info "Initializing database..."

    # Run Flyway migrations
    ./gradlew flywayMigrate

    # Create event store schema
    log_info "Creating event store schema..."
    docker exec -i eaf-postgres psql -U eaf -d eaf < scripts/sql/event-store-schema.sql

    # Create test data (optional)
    if [ "${SKIP_TEST_DATA:-false}" != "true" ]; then
        log_info "Creating test data..."
        docker exec -i eaf-postgres psql -U eaf -d eaf < scripts/sql/test-data.sql
    fi

    log_success "Database initialization complete"
}

# Configure Keycloak
configure_keycloak() {
    log_info "Configuring Keycloak..."

    # Wait for Keycloak admin API
    timeout 60 bash -c 'until curl -sf -u admin:admin http://localhost:8180/admin/realms >/dev/null; do sleep 2; done'

    # Import EAF realm configuration
    ./scripts/keycloak-setup.sh

    log_success "Keycloak configuration complete"
}

# Build project
build_project() {
    log_info "Building project..."

    # Clean and build without tests (tests run separately)
    ./gradlew clean build -x test -x integrationTest

    log_success "Project build complete"
}

# Run quality checks
run_quality_checks() {
    log_info "Running quality gates..."

    # Code formatting
    log_info "Checking code formatting..."
    ./gradlew ktlintCheck

    # Static analysis
    log_info "Running static analysis..."
    ./gradlew detekt

    # Architecture tests
    log_info "Validating architecture..."
    ./gradlew konsistTest

    log_success "All quality checks passed"
}

# Run tests
run_tests() {
    if [ "${SKIP_TESTS:-false}" == "true" ]; then
        log_warning "Skipping tests (SKIP_TESTS=true)"
        return
    fi

    log_info "Running test suite..."

    # Fast tests first (nullable pattern)
    log_info "Running fast business logic tests..."
    ./gradlew test -P fastTests=true

    # Integration tests
    log_info "Running integration tests..."
    ./gradlew integrationTest

    log_success "All tests passed"
}

# Start application services
start_application() {
    log_info "Starting EAF application services..."

    # Start backend in background
    log_info "Starting backend application..."
    ./gradlew :products:licensing-server:bootRun > logs/backend.log 2>&1 &
    BACKEND_PID=$!
    echo $BACKEND_PID > .pids/backend.pid

    # Wait for backend to start
    log_info "Waiting for backend to start..."
    timeout 120 bash -c 'until curl -sf http://localhost:8080/actuator/health >/dev/null; do sleep 3; done'
    log_success "Backend application is running"

    # Start frontend if Node.js is available
    if command -v npm >/dev/null 2>&1; then
        log_info "Starting frontend application..."
        cd apps/admin
        npm install --silent
        npm run dev > ../../logs/frontend.log 2>&1 &
        FRONTEND_PID=$!
        echo $FRONTEND_PID > ../../.pids/frontend.pid
        cd ../..

        # Wait for frontend
        timeout 60 bash -c 'until curl -sf http://localhost:3000 >/dev/null; do sleep 3; done'
        log_success "Frontend application is running"
    else
        log_warning "Skipping frontend (Node.js not available)"
    fi
}

# Create necessary directories
setup_directories() {
    mkdir -p logs .pids
}

# Cleanup function
cleanup() {
    log_info "Cleaning up..."

    # Kill application processes
    if [ -f .pids/backend.pid ]; then
        kill $(cat .pids/backend.pid) 2>/dev/null || true
        rm .pids/backend.pid
    fi

    if [ -f .pids/frontend.pid ]; then
        kill $(cat .pids/frontend.pid) 2>/dev/null || true
        rm .pids/frontend.pid
    fi
}

# Trap cleanup on exit
trap cleanup EXIT

# Main execution
main() {
    local start_time=$(date +%s)

    setup_directories
    check_prerequisites
    start_infrastructure
    initialize_database
    configure_keycloak
    build_project
    run_quality_checks
    run_tests
    start_application

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    echo
    echo "================================================================"
    log_success "EAF Development Environment Ready! (${duration}s)"
    echo "================================================================"
    echo
    echo "🌐 Services Available:"
    echo "   • Backend API:      http://localhost:8080"
    echo "   • API Documentation: http://localhost:8080/swagger-ui.html"
    echo "   • Admin Portal:     http://localhost:3000"
    echo "   • Keycloak:         http://localhost:8180"
    echo "   • Grafana:          http://localhost:3001"
    echo "   • Prometheus:       http://localhost:9090"
    echo
    echo "📋 Quick Commands:"
    echo "   • Stop services:    ./scripts/stop-dev.sh"
    echo "   • View logs:        ./scripts/logs.sh"
    echo "   • Run tests:        ./gradlew test"
    echo "   • Generate code:    eaf scaffold --help"
    echo
    echo "📖 Documentation: docs/README.md"
    echo

    # Keep the script running to maintain services
    if [ "${DETACH:-false}" != "true" ]; then
        echo "Press Ctrl+C to stop all services..."
        wait
    fi
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            export SKIP_TESTS=true
            shift
            ;;
        --skip-test-data)
            export SKIP_TEST_DATA=true
            shift
            ;;
        --detach)
            export DETACH=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  --skip-tests      Skip running tests"
            echo "  --skip-test-data  Skip creating test data"
            echo "  --detach          Run in background mode"
            echo "  --help           Show this help"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

main "$@"
```

### Supporting Scripts

```bash
# scripts/stop-dev.sh - Stop all development services
#!/bin/bash

echo "🛑 Stopping EAF development environment..."

# Stop application processes
if [ -f .pids/backend.pid ]; then
    echo "Stopping backend application..."
    kill $(cat .pids/backend.pid) 2>/dev/null || true
    rm .pids/backend.pid
fi

if [ -f .pids/frontend.pid ]; then
    echo "Stopping frontend application..."
    kill $(cat .pids/frontend.pid) 2>/dev/null || true
    rm .pids/frontend.pid
fi

# Stop Docker services
echo "Stopping infrastructure services..."
docker compose -f docker-compose.dev.yml down

echo "✅ Development environment stopped"
```

```bash
# scripts/logs.sh - View application logs
#!/bin/bash

case "${1:-all}" in
    backend)
        tail -f logs/backend.log
        ;;
    frontend)
        tail -f logs/frontend.log
        ;;
    postgres)
        docker logs -f eaf-postgres
        ;;
    keycloak)
        docker logs -f eaf-keycloak
        ;;
    redis)
        docker logs -f eaf-redis
        ;;
    all)
        echo "Available logs: backend, frontend, postgres, keycloak, redis"
        echo "Usage: $0 <service-name>"
        ;;
    *)
        echo "Unknown service: $1"
        exit 1
        ;;
esac
```

## Scaffolding CLI

### Installation and Setup

```bash
# Build and install the CLI tool
./gradlew :tools:cli:installDist

# Add to PATH (optional)
export PATH="$PATH:$(pwd)/tools/cli/build/install/eaf-cli/bin"

# Verify installation
eaf --version
```

### Scaffolding Commands

```bash
# Generate a new Spring Modulith module
eaf scaffold module security:authentication
# Creates:
# - framework/security/authentication/
# - ModuleMetadata class with @ApplicationModule
# - Package structure and basic configuration

# Generate a CQRS aggregate
eaf scaffold aggregate License --events Created,Issued,Revoked,Suspended
# Creates:
# - Aggregate class with command handlers
# - Event classes
# - Command classes
# - Event sourcing handlers

# Generate REST API endpoints
eaf scaffold api-resource License --path /api/v1/licenses --operations create,read,update,delete
# Creates:
# - Controller class with OpenAPI annotations
# - Request/Response DTOs
# - Validation annotations
# - Error handling

# Generate test specifications
eaf scaffold test LicenseService --type integration --spec behavior
# Creates:
# - Kotest BehaviorSpec for business scenarios
# - Nullable pattern setup for dependencies
# - Test data builders
# - Tenant isolation test cases

# Generate complete feature
eaf scaffold feature ProductCatalog --aggregate Product --api /api/v1/products --events Created,Updated,Discontinued
# Creates complete feature with:
# - Domain aggregate
# - API endpoints
# - Event handlers
# - Test specifications
# - Database projections
```

### Custom Templates

```kotlin
// tools/cli/src/main/resources/templates/aggregate/{{aggregateName}}.kt.mustache
@Aggregate
class {{aggregateName}} {

    @AggregateIdentifier
    private lateinit var {{aggregateName.toLowerCase}}Id: String
    private lateinit var tenantId: String
    private var status: {{aggregateName}}Status = {{aggregateName}}Status.DRAFT

    @CommandHandler
    constructor(command: Create{{aggregateName}}Command) {
        // Validation
        require(command.tenantId.isNotBlank()) { "Tenant ID cannot be blank" }

        apply({{aggregateName}}CreatedEvent(
            {{aggregateName.toLowerCase}}Id = command.{{aggregateName.toLowerCase}}Id,
            tenantId = command.tenantId
            {{#events}}
            {{#isFirst}},{{/isFirst}}
            {{/events}}
        ))
    }

    {{#events}}
    @EventSourcingHandler
    fun on(event: {{aggregateName}}{{.}}Event) {
        {{#isCreated}}
        this.{{aggregateName.toLowerCase}}Id = event.{{aggregateName.toLowerCase}}Id
        this.tenantId = event.tenantId
        this.status = {{aggregateName}}Status.ACTIVE
        {{/isCreated}}
        {{^isCreated}}
        // Handle {{.}} event
        {{/isCreated}}
    }

    {{/events}}
}

enum class {{aggregateName}}Status {
    DRAFT,
    ACTIVE,
    {{#events}}
    {{#isStatus}}{{.}},{{/isStatus}}
    {{/events}}
}
```

## Daily Development Commands

### Fast Development Cycle

```bash
# Quick feedback loop (< 30 seconds)
./gradlew test -P fastTests=true          # Fast nullable pattern tests
./gradlew ktlintCheck                     # Code formatting
./gradlew detekt                          # Static analysis

# Complete validation (2-5 minutes)
./gradlew clean build                     # Full build with all tests
./gradlew integrationTest                 # Integration tests with Testcontainers
./gradlew konsistTest                     # Architecture compliance

# Performance validation
./gradlew pitest                          # Mutation testing (10-15 minutes)
```

### Development Profiles

```yaml
# application-dev.yml - Development profile
spring:
  profiles:
    active: dev
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: validate
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/eaf

logging:
  level:
    com.axians.eaf: DEBUG
    org.springframework.security: DEBUG
    org.axonframework: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always

eaf:
  security:
    jwt:
      validation:
        enabled: true
        strict-mode: false  # Lenient for development
  tenancy:
    enforcement: WARN  # Warning only in dev
```

```yaml
# application-test.yml - Test profile
spring:
  profiles:
    active: test
  datasource:
    url: ${TESTCONTAINERS_POSTGRES_URL}
    username: ${TESTCONTAINERS_POSTGRES_USERNAME}
    password: ${TESTCONTAINERS_POSTGRES_PASSWORD}

eaf:
  security:
    jwt:
      validation:
        profile: security-lite  # Fast validation for tests
  testing:
    nullable-pattern: true
    container-reuse: true
```

### Git Workflow

```bash
# Feature development workflow
git checkout -b feature/EAF-123-license-validation
git commit -m "[EAF-123] Add license validation service

- Implement 10-layer validation pipeline
- Add tenant isolation checks
- Include comprehensive test coverage

🤖 Generated with Claude Code"

# Before pushing - run quality gates
./gradlew clean build
git push origin feature/EAF-123-license-validation

# Create pull request
gh pr create --title "[EAF-123] Add license validation service" \
  --body "Implements comprehensive license validation with security and tenant isolation"
```

### Pre-commit Hooks

```bash
#!/bin/sh
# .git/hooks/pre-commit

echo "Running pre-commit quality checks..."

# Format code
./gradlew ktlintFormat

# Check formatting
if ! ./gradlew ktlintCheck; then
    echo "❌ Code formatting failed. Run: ./gradlew ktlintFormat"
    exit 1
fi

# Static analysis
if ! ./gradlew detekt; then
    echo "❌ Static analysis failed. Fix detekt violations."
    exit 1
fi

# Fast tests
if ! ./gradlew test -P fastTests=true; then
    echo "❌ Fast tests failed. Fix test failures."
    exit 1
fi

echo "✅ Pre-commit checks passed"
```

## IDE Configuration

### IntelliJ IDEA Setup

```kotlin
// .idea/codeStyles/Project.xml
<component name="ProjectCodeStyleConfiguration">
  <code_scheme name="Project" version="173">
    <JetCodeStyleSettings>
      <option name="PACKAGES_TO_USE_STAR_IMPORTS">
        <value />
      </option>
      <option name="NAME_COUNT_TO_USE_STAR_IMPORT" value="2147483647" />
      <option name="NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS" value="2147483647" />
    </JetCodeStyleSettings>
    <codeStyleSettings language="kotlin">
      <option name="CODE_STYLE_DEFAULTS" value="KOTLIN_OFFICIAL" />
    </codeStyleSettings>
  </code_scheme>
</component>
```

```properties
# .idea/gradle.xml - Gradle configuration
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
      <GradleProjectSettings>
        <option name="distributionType" value="WRAPPER" />
        <option name="externalProjectPath" value="$PROJECT_DIR$" />
        <option name="gradleJvm" value="21" />
        <option name="modules">
          <set>
            <option value="$PROJECT_DIR$" />
            <option value="$PROJECT_DIR$/framework" />
            <option value="$PROJECT_DIR$/products" />
            <option value="$PROJECT_DIR$/shared" />
          </set>
        </option>
      </GradleProjectSettings>
    </option>
  </component>
</project>
```

### VS Code Configuration

```json
// .vscode/settings.json
{
  "kotlin.languageServer.enabled": true,
  "kotlin.debugAdapter.enabled": true,
  "java.configuration.runtimes": [
    {
      "name": "JavaSE-21",
      "path": "/usr/lib/jvm/java-21-openjdk"
    }
  ],
  "files.exclude": {
    "**/build": true,
    "**/.gradle": true,
    "**/node_modules": true
  },
  "search.exclude": {
    "**/build": true,
    "**/.gradle": true,
    "**/node_modules": true
  }
}
```

```json
// .vscode/tasks.json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "build",
      "type": "shell",
      "command": "./gradlew build",
      "group": "build",
      "presentation": {
        "echo": true,
        "reveal": "always",
        "focus": false,
        "panel": "shared"
      }
    },
    {
      "label": "test-fast",
      "type": "shell",
      "command": "./gradlew test -P fastTests=true",
      "group": "test",
      "presentation": {
        "echo": true,
        "reveal": "always",
        "focus": false,
        "panel": "shared"
      }
    },
    {
      "label": "format",
      "type": "shell",
      "command": "./gradlew ktlintFormat",
      "group": "build"
    }
  ]
}
```

## Debugging and Troubleshooting

### Debug Configuration

```bash
# Start backend with debug port
JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
./gradlew :products:licensing-server:bootRun

# Debug tests
./gradlew test --debug-jvm

# Debug with specific profile
SPRING_PROFILES_ACTIVE=dev,debug \
./gradlew :products:licensing-server:bootRun
```

### Common Issues and Solutions

```bash
# Issue: Testcontainers failing to start
# Solution: Check Docker daemon and clean up
docker system prune -f
docker volume prune -f

# Issue: Port conflicts
# Solution: Find and kill processes using ports
lsof -ti:8080 | xargs kill -9  # Backend port
lsof -ti:3000 | xargs kill -9  # Frontend port
lsof -ti:5432 | xargs kill -9  # PostgreSQL port

# Issue: Gradle daemon issues
# Solution: Stop daemon and clean cache
./gradlew --stop
rm -rf ~/.gradle/caches/

# Issue: Node modules conflicts
# Solution: Clean install
cd apps/admin
rm -rf node_modules package-lock.json
npm install

# Issue: Database migration conflicts
# Solution: Reset test database
docker exec eaf-postgres psql -U eaf -c "DROP SCHEMA IF EXISTS eaf_test CASCADE;"
./gradlew flywayMigrate
```

### Performance Monitoring

```bash
# Monitor test performance
./gradlew test -P profile=true

# Monitor build performance
./gradlew build --profile --scan

# Monitor memory usage
./gradlew :products:licensing-server:bootRun -Dspring.profiles.active=dev,monitoring
```

## Continuous Integration Integration

### GitHub Actions Workflow

```yaml
# .github/workflows/ci.yml
name: Continuous Integration

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  quality-gates:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Cache Gradle dependencies
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Run quality gates
        run: |
          ./gradlew ktlintCheck
          ./gradlew detekt
          ./gradlew konsistTest

  test:
    needs: quality-gates
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Run tests
        run: |
          ./gradlew test integrationTest
          ./gradlew jacocoTestReport

      - name: Upload coverage reports
        uses: codecov/codecov-action@v4
        with:
          files: ./build/reports/jacoco/test/jacocoTestReport.xml

  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run security scan
        run: ./gradlew dependencyCheckAnalyze
```

## Related Documentation

- **[Technology Stack](tech-stack.md)** - Tools and versions used in development
- **[Testing Strategy](test-strategy-and-standards-revision-3.md)** - Testing practices and patterns
- **[Coding Standards](coding-standards-revision-2.md)** - Code quality requirements
- **[System Components](components.md)** - Scaffolding CLI implementation
- **[Deployment Architecture](deployment-architecture-revision-2.md)** - Production deployment procedures

---

**Next Steps**: Review [Coding Standards](coding-standards-revision-2.md) for implementation guidelines, then proceed to [System Components](components.md) for scaffolding CLI usage examples.