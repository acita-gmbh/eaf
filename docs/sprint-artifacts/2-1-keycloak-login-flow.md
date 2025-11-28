# Story 2.1: Keycloak Login Flow

Status: drafted

## Story

As an **end user**,
I want to log in via Keycloak SSO,
so that I can access DVMM securely with my company credentials.

## Requirements Context Summary

- **Epic/AC source:** Story 2.1 in `docs/epics.md` — Keycloak OIDC login/logout with session management.
- **FRs Satisfied:** FR1 (SSO Authentication), FR2 (Session Management), FR7a (Token Refresh)
- **Architecture constraint:** Frontend Tracer Bullet — first user-facing code validating complete UI stack.
- **Prerequisites:** Story 1.7 (Keycloak Integration) — backend SecurityConfig completed.
- **Technical Debt:** Coverage restoration required for `eaf-auth-keycloak` (15% → ≥80%) and `dvmm-api` (54% → ≥80%).
- **Token Storage:** httpOnly cookie with `Secure` and `SameSite=Lax` flags, explicit CSRF token validation.

## Acceptance Criteria

1. **Unauthenticated redirect to Keycloak**
   - Given I navigate to DVMM root URL (`/`)
   - When I am not authenticated
   - Then I am redirected to Keycloak login page.

2. **Successful login redirects to dashboard**
   - Given I complete Keycloak login successfully
   - When Keycloak redirects back to DVMM
   - Then I see the DVMM dashboard
   - And my session is maintained via httpOnly cookie (Secure, SameSite=Lax)
   - And my name and tenant are displayed in the header.

3. **Logout clears session**
   - Given I am logged in
   - When I click the logout button
   - Then I am redirected to Keycloak logout
   - And my session is cleared (cookies removed)
   - And I am redirected to login page.

4. **Token refresh is transparent**
   - Given my JWT token expires during a session
   - When I perform any action requiring authentication
   - Then the frontend transparently refreshes the token
   - And if refresh fails, I am redirected to login.

5. **CSRF protection on mutations**
   - Given I am authenticated
   - When I make a state-changing request (POST/PUT/DELETE)
   - Then the request includes X-CSRF-Token header
   - And backend validates the token before processing.

6. **Coverage restored for eaf-auth-keycloak (≥80%)**
   - Given `eaf-auth-keycloak` has coverage verification disabled
   - When this story is complete
   - Then Keycloak Testcontainer integration tests exist for `KeycloakIdentityProvider`
   - And module achieves ≥80% test coverage
   - And `tasks.named("koverVerify") { enabled = false }` is removed from `eaf/eaf-auth-keycloak/build.gradle.kts`.

7. **Coverage restored for dvmm-api (≥80%)**
   - Given `dvmm-api` has coverage verification disabled
   - When this story is complete
   - Then SecurityConfig integration tests verify:
     - Unauthenticated `/api/**` requests return 401
     - Unauthenticated `/actuator/health` requests are allowed
     - Authenticated requests with valid JWT succeed
   - And module achieves ≥80% test coverage
   - And `tasks.named("koverVerify") { enabled = false }` is removed from `dvmm/dvmm-api/build.gradle.kts`.

8. **Pitest restored for eaf-auth-keycloak (≥70%)**
   - Given `eaf-auth-keycloak` has mutation testing disabled
   - When this story is complete
   - Then module achieves ≥70% mutation score
   - And Pitest exclusion is removed from `eaf/eaf-auth-keycloak/build.gradle.kts`.

## Test Plan

- **Integration Test:** Keycloak Testcontainer validates full OIDC flow (login, token exchange, logout)
- **Integration Test:** SecurityConfig tests with mock JWT tokens (valid/invalid/expired)
- **Integration Test:** CSRF token validation on POST endpoints
- **Unit Test:** Token refresh logic in frontend (mock Keycloak responses)
- **E2E Test (Playwright):** Full login/logout flow with real Keycloak Testcontainer
- **Coverage Verification:** `./gradlew koverVerify` passes for all modules

## Structure Alignment / Previous Learnings

### Learnings from Previous Story

#### From Story 1-11-cicd-quality-gates (Status: done)

- **Coverage Exclusions Currently in Place:**
  1. `eaf-auth-keycloak` module: 15% vs 80% required
     - Location: `eaf/eaf-auth-keycloak/build.gradle.kts` - `tasks.named("koverVerify") { enabled = false }`
     - Reason: Story 1.7 created implementation without Keycloak Testcontainer tests
  2. `dvmm-api` module: 54% vs 80% required
     - Location: `dvmm/dvmm-api/build.gradle.kts` - `tasks.named("koverVerify") { enabled = false }`
     - Reason: SecurityConfig requires Spring Security WebFlux integration tests

- **Pitest Exclusions Currently in Place:**
  1. `eaf-auth-keycloak`: 12% mutation score
     - Location: `eaf/eaf-auth-keycloak/build.gradle.kts`
     - Must be restored after adding tests

- **CI Pipeline:** `.github/workflows/ci.yml` — all quality gates must pass before merge

[Source: docs/sprint-artifacts/1-11-cicd-quality-gates.md#Completion-Notes-List]

### Project Structure Notes

- Frontend: `dvmm/dvmm-web/` (new React application with Vite)
- Backend: Existing modules from Epic 1
- Auth module: `eaf/eaf-auth-keycloak/` (implementation exists, needs tests)
- API module: `dvmm/dvmm-api/` (SecurityConfig exists, needs tests)
- Test resources: `src/test/resources/` for test configuration

## Tasks / Subtasks

- [ ] **Task 1: Setup Keycloak Testcontainer** (AC: 6, 7, 8)
  - [ ] Add Keycloak Testcontainer dependency to `eaf-testing` module
  - [ ] Create `KeycloakTestcontainer` fixture (realm import, test users)
  - [ ] Configure test realm JSON with `dvmm` realm, clients, users
  - [ ] Verify Testcontainer starts and accepts logins

- [ ] **Task 2: Write eaf-auth-keycloak integration tests** (AC: 6, 8)
  - [ ] Create `KeycloakIdentityProviderIntegrationTest`
  - [ ] Test: getUserInfo() returns correct user details from token
  - [ ] Test: getTenantId() extracts tenant_id claim correctly
  - [ ] Test: Invalid token throws appropriate exception
  - [ ] Achieve ≥80% coverage on `eaf-auth-keycloak`
  - [ ] Remove `koverVerify { enabled = false }` from build.gradle.kts
  - [ ] Restore Pitest threshold, achieve ≥70% mutation score

- [ ] **Task 3: Write dvmm-api SecurityConfig tests** (AC: 7)
  - [ ] Create `SecurityConfigIntegrationTest` with WebTestClient
  - [ ] Test: GET /api/** without token returns 401
  - [ ] Test: GET /actuator/health without token returns 200
  - [ ] Test: GET /api/** with valid JWT returns 200
  - [ ] Test: POST /api/** without CSRF token returns 403
  - [ ] Test: POST /api/** with valid CSRF token succeeds
  - [ ] Achieve ≥80% coverage on `dvmm-api`
  - [ ] Remove `koverVerify { enabled = false }` from build.gradle.kts

- [ ] **Task 4: Initialize frontend application** (AC: 1, 2, 3, 4)
  - [ ] Create `dvmm/dvmm-web/` with Vite + React + TypeScript
  - [ ] Add shadcn/ui with Tailwind CSS (Tech Teal theme)
  - [ ] Configure `react-oidc-context` for Keycloak integration
  - [ ] Setup environment variables for Keycloak URL, realm, client

- [ ] **Task 5: Implement login flow** (AC: 1, 2)
  - [ ] Create AuthProvider wrapper with Keycloak configuration
  - [ ] Create ProtectedRoute component (redirects to login if unauthenticated)
  - [ ] Implement login redirect to Keycloak
  - [ ] Handle callback from Keycloak, store tokens

- [ ] **Task 6: Implement session management** (AC: 2, 4)
  - [ ] Configure httpOnly cookie storage for tokens
  - [ ] Implement transparent token refresh before expiration
  - [ ] Handle refresh failure (redirect to login)
  - [ ] Display user name and tenant in header component

- [ ] **Task 7: Implement logout** (AC: 3)
  - [ ] Create logout button in header
  - [ ] Implement Keycloak logout flow
  - [ ] Clear local session state
  - [ ] Redirect to Keycloak logout endpoint

- [ ] **Task 8: Implement CSRF protection** (AC: 5)
  - [ ] Add CSRF token endpoint to backend (/api/csrf)
  - [ ] Configure frontend to fetch CSRF token on auth
  - [ ] Add X-CSRF-Token header to all mutation requests
  - [ ] Backend validates CSRF token on POST/PUT/DELETE

- [ ] **Task 9: E2E tests with Playwright** (AC: 1, 2, 3)
  - [ ] Setup Playwright with Keycloak Testcontainer
  - [ ] Test: Full login flow (redirect, login, callback)
  - [ ] Test: Session persistence across page refresh
  - [ ] Test: Logout clears session

## Dev Notes

- **Relevant architecture patterns:**
  - OIDC Authorization Code Flow with PKCE
  - httpOnly cookies for token storage (XSS mitigation)
  - CSRF token validation (CSRF mitigation)
  - BFF pattern: API validates tokens, frontend handles OIDC flow

- **Source tree components to touch:**
  - `eaf/eaf-testing/` — Keycloak Testcontainer fixture
  - `eaf/eaf-auth-keycloak/src/test/` — Integration tests
  - `dvmm/dvmm-api/src/test/` — SecurityConfig tests
  - `dvmm/dvmm-web/` — New React frontend application
  - `build.gradle.kts` files — Remove coverage exclusions

- **Testing standards:**
  - Tests First pattern: Write integration tests before frontend implementation
  - Keycloak Testcontainer for realistic OIDC testing
  - MockK for unit tests, real containers for integration tests

### Keycloak Test Realm Configuration

```json
{
  "realm": "dvmm-test",
  "enabled": true,
  "clients": [
    {
      "clientId": "dvmm-web",
      "publicClient": true,
      "redirectUris": ["http://localhost:5173/*"],
      "webOrigins": ["+"]
    },
    {
      "clientId": "dvmm-api",
      "bearerOnly": true
    }
  ],
  "users": [
    {
      "username": "testuser",
      "enabled": true,
      "credentials": [{"type": "password", "value": "test"}],
      "attributes": {"tenant_id": ["tenant-1"]}
    }
  ]
}
```

### References

- [Source: docs/epics.md#Story-2.1-Keycloak-Login-Flow]
- [Source: docs/sprint-artifacts/tech-spec-epic-2.md#Section-2.1-In-Scope]
- [Source: docs/sprint-artifacts/tech-spec-epic-2.md#Section-2.3-Technical-Debt]
- [Source: docs/architecture.md#Authentication]
- [Source: docs/ux-design-specification.md#Login-Screen]
- [Source: docs/sprint-artifacts/1-11-cicd-quality-gates.md#Completion-Notes-List]
- [Source: CLAUDE.md#Zero-Tolerance-Policies]

## Dev Agent Record

### Context Reference

<!-- Path(s) to story context XML will be added here by context workflow -->

### Agent Model Used

claude-opus-4-5-20251101

### Debug Log References

### Completion Notes List

### File List

### Change Log

- 2025-11-28: Story drafted from epics.md, tech-spec-epic-2.md, and learnings from Story 1.11
