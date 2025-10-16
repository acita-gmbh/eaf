# Authentication Flow Migration Plan

**Status**: Production Blocker (MEDIUM Severity)
**Target**: Before Production Deployment
**Related**: Story 9.1 Security Finding #2

## Overview

The current React-Admin authentication implementation uses **OAuth 2.0 Password Grant** (Resource Owner Password Credentials), which is:
- Deprecated by OAuth 2.0 Security Best Current Practice (RFC 8252)
- Removed entirely from OAuth 2.1
- Exposes credentials to JavaScript runtime (XSS theft risk)
- Lacks phishing protection

This document outlines the migration path to **Authorization Code Flow with PKCE**, which is the recommended secure authentication pattern for Single Page Applications (SPAs).

## Current Implementation (Password Grant)

### Security Risks

1. **Credential Exposure**: Username/password handled in JavaScript
   - Vulnerable to XSS attacks (any script can intercept)
   - Vulnerable to malicious browser extensions
   - Vulnerable to compromised dependencies
   - No protection from browser developer tools inspection

2. **Token Storage**: JWT tokens in localStorage
   - Accessible to any JavaScript (XSS token theft)
   - No secure storage option in browser
   - Refresh tokens exposed

3. **Phishing**: Users cannot distinguish legitimate login from fake
   - Credentials entered directly in SPA
   - No Keycloak-hosted login page
   - Social engineering risk

### Current Mitigations (Insufficient for Production)

✅ **Active Defense-in-Depth**:
- DOMPurify sanitization on all user inputs
- React auto-escaping (no dangerouslySetInnerHTML found)
- Short-lived tokens (15-minute expiration)
- Backend 10-layer JWT validation

⚠️ **Limitations**:
- Mitigations reduce but don't eliminate risk
- XSS zero-days can still bypass protections
- Credentials in JavaScript is inherently insecure

## Target Implementation (Authorization Code Flow + PKCE)

### Architecture

```
User → Frontend (SPA) → Keycloak (Auth Server) → Frontend → Backend API
                ↓
            Redirect to Keycloak Login Page
            (Credentials NEVER enter SPA)
                ↓
            User authenticates on Keycloak domain
                ↓
            Keycloak redirects back with auth code
                ↓
            Frontend exchanges code for tokens (PKCE protects exchange)
                ↓
            Tokens stored in httpOnly cookies (XSS-proof)
```

### Key Security Improvements

1. **No Credential Exposure**:
   - User authenticates on Keycloak-hosted page (different domain)
   - JavaScript never sees username/password
   - XSS in SPA cannot steal credentials

2. **PKCE Protection**:
   - Prevents authorization code interception
   - Code verifier + challenge prevents MITM
   - No client secret needed (public client secure)

3. **Secure Token Storage**:
   - Refresh tokens in httpOnly cookies (JavaScript cannot access)
   - Access tokens can be in localStorage (short-lived, frequently refreshed)
   - XSS can steal access token but not refresh token

4. **Phishing Protection**:
   - Users verify Keycloak domain before entering credentials
   - Login page branding controlled by Keycloak admin
   - Harder to create convincing fake login

## Migration Steps

### Phase 1: Keycloak Configuration

**File**: `apps/admin/KEYCLOAK_SETUP.md` (update)

1. **Disable Password Grant**:
   ```
   Clients → eaf-admin → Settings → Capability config:
   - Direct access grants: OFF ← Change this
   - Standard flow: ON
   - Implicit flow: OFF
   ```

2. **Add Custom Audience** (recommended):
   ```
   Clients → eaf-admin → Client scopes → eaf-admin-dedicated → Add mapper:
   - Mapper Type: Audience
   - Name: eaf-backend-audience
   - Included Client Audience: eaf-backend
   - Add to access token: ON
   ```

3. **Configure Redirect URIs** (already done):
   - http://localhost:5173/*
   - https://admin.eaf.example.com/*

### Phase 2: Frontend Implementation

**Library**: Use `@react-keycloak/web` (official Keycloak React adapter)

**Installation**:
```bash
cd framework/admin-shell
pnpm add @react-keycloak/web keycloak-js
```

**Replace**: `framework/admin-shell/src/providers/authProvider.ts`

**New Implementation**:
```typescript
import Keycloak from 'keycloak-js';
import { ReactKeycloakProvider } from '@react-keycloak/web';
import type { AuthProvider } from 'react-admin';

// Keycloak instance with Authorization Code Flow config
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT,
});

// Keycloak initialization options
const keycloakInitOptions = {
  onLoad: 'login-required',  // Redirect to login if not authenticated
  checkLoginIframe: false,   // Disable for localhost development
  pkceMethod: 'S256',        // Use SHA-256 for PKCE (most secure)
  flow: 'standard',          // Authorization Code Flow
};

// React-Admin auth provider wrapping Keycloak
export function createAuthProvider(): AuthProvider {
  return {
    login: async () => {
      // Handled by Keycloak redirect
      return Promise.resolve();
    },

    logout: async () => {
      await keycloak.logout();
      return Promise.resolve();
    },

    checkAuth: async () => {
      return keycloak.authenticated
        ? Promise.resolve()
        : Promise.reject(new Error('Not authenticated'));
    },

    checkError: async (error) => {
      if (error?.status === 401 || error?.status === 403) {
        await keycloak.logout();
        return Promise.reject();
      }
      return Promise.resolve();
    },

    getPermissions: async () => {
      // Extract from Keycloak token (validated on backend)
      return keycloak.tokenParsed?.realm_access?.roles || [];
    },

    getIdentity: async () => {
      return {
        id: keycloak.tokenParsed?.sub,
        fullName: keycloak.tokenParsed?.name || keycloak.tokenParsed?.preferred_username,
      };
    },
  };
}

// Get access token for API calls (injected into Authorization header)
export function getAccessToken(): string | undefined {
  return keycloak.token;
}

// Export Keycloak instance and provider for App.tsx
export { keycloak, ReactKeycloakProvider };
```

**Update**: `apps/admin/src/App.tsx`

```typescript
import { ReactKeycloakProvider, keycloak, createAuthProvider } from '@axians/eaf-admin-shell';
import { Admin } from 'react-admin';

const App = () => (
  <ReactKeycloakProvider authClient={keycloak} initOptions={keycloakInitOptions}>
    <Admin
      authProvider={createAuthProvider()}
      dataProvider={createDataProvider()}
      // ...resources
    />
  </ReactKeycloakProvider>
);
```

### Phase 3: Backend Configuration

**File**: `products/*/src/main/kotlin/.../SecurityConfiguration.kt`

**Add Cookie Support** (for refresh tokens):
```kotlin
@Configuration
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            // SECURITY: Enable secure cookie handling
            .csrf { csrf -> csrf.disable() } // JWT-based auth, CSRF not needed
            .headers { headers ->
                headers.frameOptions { it.deny() }
                headers.contentSecurityPolicy {
                    it.policyDirectives("default-src 'self'; frame-ancestors 'none'")
                }
            }

        return http.build()
    }
}
```

**Note**: Backend already validates JWTs via 10-layer validation. Authorization Code Flow tokens are validated identically.

### Phase 4: Token Refresh Strategy

**Current** (Password Grant):
- Frontend manually refreshes using stored refresh token
- Refresh token in localStorage (vulnerable to XSS)

**Target** (Authorization Code + httpOnly Cookies):
- Keycloak sets refresh token in httpOnly cookie (XSS-proof)
- Frontend refresh via Keycloak `updateToken()` method
- Automatic silent refresh before expiration
- No JavaScript access to refresh token

**Implementation**:
```typescript
// In dataProvider.ts - before each API call
keycloak.updateToken(60).then((refreshed) => {
  if (refreshed) {
    console.log('Token refreshed');
  }
  // Proceed with API call using keycloak.token
}).catch(() => {
  console.error('Failed to refresh token');
  keycloak.logout();
});
```

### Phase 5: Testing & Validation

**Manual Testing Checklist**:
- [ ] Login redirects to Keycloak page (not in-app form)
- [ ] Authentication succeeds and redirects back to app
- [ ] JWT token acquired and sent in Authorization header
- [ ] Refresh token in httpOnly cookie (inspect in DevTools → Application → Cookies)
- [ ] Access token NOT in cookies (localStorage OK for short-lived)
- [ ] Token refresh works automatically before expiration
- [ ] Logout clears all tokens and cookies
- [ ] Unauthenticated users redirected to Keycloak login

**Security Validation**:
- [ ] Inspect localStorage: Should NOT contain refresh_token
- [ ] Inspect cookies: Should contain KC_RESTART (httpOnly=true)
- [ ] Network tab: Authorization header present in API calls
- [ ] XSS test: Inject `<script>alert(localStorage)</script>` → Should not see refresh token

## Migration Timeline

**Estimated Effort**: 2-3 days

| Phase | Tasks | Duration | Owner |
|-------|-------|----------|-------|
| **Phase 1** | Keycloak config changes | 1 hour | DevOps/Dev |
| **Phase 2** | Frontend implementation | 1 day | Frontend Dev |
| **Phase 3** | Backend cookie support (if needed) | 2 hours | Backend Dev |
| **Phase 4** | Token refresh implementation | 4 hours | Frontend Dev |
| **Phase 5** | Testing & validation | 1 day | QA + Dev |

**Total**: 2-3 days (assuming no blockers)

## Rollback Plan

If migration encounters issues:

1. **Quick Rollback**: Revert frontend PR, re-enable Direct Access Grants in Keycloak
2. **Estimated Time**: 30 minutes
3. **Risk**: Low (Password Grant still works, just less secure)

## Success Criteria

✅ **Functional**:
- Users can log in via Keycloak-hosted page
- Authentication flow completes without errors
- All existing features work (CRUD, tenant isolation, etc.)

✅ **Security**:
- No credentials in JavaScript runtime
- Refresh tokens in httpOnly cookies
- PKCE code verifier/challenge working
- XSS cannot steal refresh tokens

✅ **Performance**:
- Login flow completes in <3 seconds
- Token refresh transparent to user
- No degradation in API call latency

## References

- **OAuth 2.0 Security BCP**: RFC 8252 (deprecates Password Grant for native/browser apps)
- **OAuth 2.1**: Draft spec (removes Password Grant entirely)
- **OWASP ASVS**: V2.2.1 (Session tokens in httpOnly cookies)
- **Keycloak Docs**: [Securing Applications and Services Guide - JavaScript Adapter](https://www.keycloak.org/docs/latest/securing_apps/#_javascript_adapter)
- **React-Keycloak**: [@react-keycloak/web](https://github.com/react-keycloak/react-keycloak)

## Story Recommendation

**Create**: Epic 3 Follow-up Story

**Title**: "Migrate React-Admin to OAuth 2.1 Authorization Code Flow with PKCE"

**Acceptance Criteria**:
1. Keycloak eaf-admin client configured for Authorization Code Flow (Direct Access Grants disabled)
2. Frontend uses @react-keycloak/web library for OIDC authentication
3. Login redirects to Keycloak-hosted page (credentials not in SPA)
4. Refresh tokens stored in httpOnly cookies (not localStorage)
5. PKCE code verifier/challenge implemented (S256 method)
6. All existing authentication flows work (login, logout, token refresh, checkAuth)
7. Security validation: XSS cannot steal refresh tokens
8. Documentation updated (KEYCLOAK_SETUP.md, security.md)

**Priority**: P0 (Production Blocker)
**Estimate**: 2-3 days
**Dependencies**: None (all infrastructure ready)

---

**Created**: 2025-10-16
**Author**: James (Full Stack Developer) via Security Review
**Reviewed**: Quinn (Test Architect) - Security Finding documented
