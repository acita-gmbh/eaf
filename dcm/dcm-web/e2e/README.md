# E2E Test Authentication Setup

This directory contains End-to-End tests for the DCM web application using Playwright.

## Test Categories

### Unauthenticated Tests
Tests that verify basic functionality without authentication:
- Login page display
- Redirect to login when accessing protected routes
- Public API endpoints

**Run command:**
```bash
npm run test:e2e
```

### Authenticated Tests
Tests that require user authentication with Keycloak:
- Admin approval queue
- Request creation and management
- Tenant-specific data access
- Role-based access control

These tests are marked with `@requires-auth` and/or `@requires-backend` tags.

## Running Authenticated Tests

### Prerequisites

1. **Backend API must be running** with Keycloak integration:
   ```bash
   cd /path/to/eaf
   ./gradlew :dcm:dcm-app:bootRun
   ```
   
   This starts:
   - Keycloak Testcontainer (dynamic port; check backend logs for actual URL)
   - Backend API on port 8081
   - PostgreSQL database

2. **Test users** are automatically configured from `test-realm.json`:
   - `test-admin` / `test` (tenant1, admin role)
   - `test-user` / `test` (tenant1, user role)
   - `tenant2-user` / `test` (tenant2, user role)

### Setup Authentication

Run the setup project to authenticate and save session state:

```bash
npm run test:e2e -- --project=setup
```

This creates:
- `playwright/.auth/admin.json` - Admin user session
- `playwright/.auth/user.json` - Regular user session

These files are gitignored as they contain session tokens.

### Run Authenticated Tests

After setup, run tests with authenticated sessions:

```bash
# Run all admin tests
npm run test:e2e -- --project=chromium-admin

# Run specific test file as admin
npm run test:e2e -- --project=chromium-admin admin-approval-queue.spec.ts

# Run as regular user
npm run test:e2e -- --project=chromium-user my-requests.spec.ts

# Run unauthenticated tests only
npm run test:e2e -- --project=chromium
```

### Debugging Authenticated Tests

Use Playwright UI mode for debugging:

```bash
# Debug with admin session
npm run test:e2e -- --project=chromium-admin --ui

# Debug with user session
npm run test:e2e -- --project=chromium-user --ui
```

## Environment Variables

Optional environment variables for custom setup:

- `BASE_URL` - Frontend application URL (default: `http://localhost:5173`)
- `KEYCLOAK_URL` - Keycloak server URL (default: `http://localhost:8080`)
- `TEST_PASSWORD` - Password for test users (default: `test`)

Example:
```bash
BASE_URL=http://localhost:3000 KEYCLOAK_URL=http://localhost:9090 npm run test:e2e -- --project=setup
```

## CI/CD Configuration

### GitHub Actions Example

```yaml
- name: Start backend with Keycloak
  run: ./gradlew :dcm:dcm-app:bootRun &
  
- name: Wait for backend
  run: npx wait-on http://localhost:8081/actuator/health
  
- name: Setup authentication
  run: npm run test:e2e -- --project=setup
  working-directory: dcm/dcm-web
  
- name: Run authenticated E2E tests
  run: npm run test:e2e -- --project=chromium-admin --project=chromium-user
  working-directory: dcm/dcm-web
```

### Skipping in CI

To skip authenticated tests in CI (current default):
- Tests are marked with `test.skip`
- CI only runs unauthenticated tests
- No setup project needed

## Troubleshooting

### "auth.json not found"
Run the setup project first:
```bash
npm run test:e2e -- --project=setup
```

### "Backend not responding"
Ensure backend is running:
```bash
curl http://localhost:8081/actuator/health
```

### "Keycloak login failed"
1. Check Keycloak is running (verify backend logs)
2. Verify test users exist in Keycloak admin console
3. Check KEYCLOAK_URL environment variable

### "Session expired"
Re-run the setup project to refresh auth sessions:
```bash
npm run test:e2e -- --project=setup
```

## Test Structure

```
e2e/
├── auth.setup.ts              # Authentication setup for all projects
├── admin-approval-queue.spec.ts  # Admin-only tests
├── my-requests.spec.ts        # User request tests
├── request-detail.spec.ts     # Request detail view tests
├── vm-request-form.spec.ts    # VM request creation tests
└── login.spec.ts              # Unauthenticated login tests
```

## Playwright Utils Library

This project uses `@seontechnologies/playwright-utils` for enhanced E2E testing capabilities:

```typescript
// Structured logging with Playwright report integration
import { log } from '@seontechnologies/playwright-utils/log'

await log.step('Performing action')  // Creates collapsible step in report
await log.success('Action completed') // Success message with green indicator
await log.debug({ data: someData })   // Object logging for debugging
```

Available utilities (see `.bmad/bmm/testarch/knowledge/` for documentation):
- `log` - Structured logging with Playwright report integration
- `api-request` - Typed HTTP client for backend API testing
- `auth-session` - Token persistence and multi-user support
- `recurse` - Polling utility for async conditions
- `intercept-network-call` - Network mocking and interception

The `tsconfig.e2e.json` is configured with `moduleResolution: "NodeNext"` to support these subpath imports.

## Best Practices

1. **Use appropriate project for test scope:**
   - `chromium-admin` for admin-only features
   - `chromium-user` for regular user features
   - `chromium` for unauthenticated tests

2. **Tag tests appropriately:**
   - Add `@requires-auth` for tests needing authentication
   - Add `@requires-backend` for tests needing backend API

3. **Clean test data:**
   - Use unique identifiers per test run
   - Clean up created resources in test cleanup
   - Use tenant isolation for parallel test execution

4. **Session management:**
   - Auth sessions are stored in `playwright/.auth/`
   - Sessions persist between test runs (faster)
   - Re-run setup if sessions expire (typically 1 hour)

## Related Documentation

- [Playwright Authentication](https://playwright.dev/docs/auth)
- [Playwright Configuration](https://playwright.dev/docs/test-configuration)
- [Keycloak Testing Guide](../../eaf/eaf-testing/README.md)
- [Test Design System](../../docs/test-design-system.md)
