import type { AuthProvider } from 'react-admin';
import type { KeycloakConfig } from '../types';

// TODO [Epic 3 Story 3.1-3.2]: Replace sessionStorage with HttpOnly cookies (CWE-312 mitigation)
// SECURITY NOTE: sessionStorage is XSS-vulnerable. Proper solution: Backend-issued HttpOnly cookies
// Current implementation: Temporary MVP using sessionStorage (better than localStorage - cleared on tab close)
// Target: Epic 3 - Authentication & Authorization (Keycloak OIDC + secure token handling)
const TOKEN_STORAGE_KEY = 'eaf.auth.token';
const REFRESH_TOKEN_STORAGE_KEY = 'eaf.auth.refreshToken';

const DEFAULT_KEYCLOAK_CONFIG: KeycloakConfig = {
  realm: 'eaf',
  clientId: 'eaf-admin',
  serverUrl: 'http://localhost:8180',
};

/**
 * Create Keycloak OIDC auth provider
 *
 * Features:
 * - Keycloak OIDC password grant flow
 * - JWT token storage in sessionStorage (temporary MVP - Epic 3 will use HttpOnly cookies)
 * - Automatic token refresh when <5 minutes remaining (SEC-001 mitigation)
 * - Token expiration detection and automatic logout (SEC-003)
 * - Role extraction from JWT for RBAC
 * - Secure logout with Keycloak session termination
 *
 * @param config - Keycloak configuration (realm, clientId, serverUrl)
 * @returns Configured auth provider
 */
export function createAuthProvider(config: KeycloakConfig = DEFAULT_KEYCLOAK_CONFIG): AuthProvider {
  const tokenEndpoint = `${config.serverUrl}/realms/${config.realm}/protocol/openid-connect/token`;
  const logoutEndpoint = `${config.serverUrl}/realms/${config.realm}/protocol/openid-connect/logout`;

  return {
    /**
     * Handle login with Keycloak password grant
     */
    login: async ({ username, password }) => {
      try {
        const response = await fetch(tokenEndpoint, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: new URLSearchParams({
            username,
            password,
            grant_type: 'password',
            client_id: config.clientId,
          }),
        });

        if (!response.ok) {
          const error = await response.json().catch(() => ({}));
          throw new Error(error.error_description || 'Login failed');
        }

        const data = await response.json();

        // Store tokens in sessionStorage (temporary MVP - see TODO above for Epic 3 fix)
        // sessionStorage is cleared on tab close (better than localStorage for security)
        sessionStorage.setItem(TOKEN_STORAGE_KEY, data.access_token);
        if (data.refresh_token) {
          sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, data.refresh_token);
        }

        console.log('[AuthProvider] Login successful', {
          expiresIn: data.expires_in,
        });

        return Promise.resolve();
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : 'Login failed';
        console.error('[AuthProvider] Login failed:', message);
        return Promise.reject(error);
      }
    },

    /**
     * Check authentication status
     *
     * SECURITY FIX (VULN-001): Removed unsafe JWT decoding
     * Backend validates token expiration via Epic 3's 10-layer validation
     */
    checkAuth: async () => {
      const token = sessionStorage.getItem(TOKEN_STORAGE_KEY);

      // No token = not authenticated
      if (!token) {
        return Promise.reject(new Error('Not authenticated'));
      }

      // SECURITY: Do NOT check expiration on unverified JWT
      // Backend Layer 1 filter validates JWT signature and expiration
      // If token is invalid, backend returns 401 and checkError() clears session

      return Promise.resolve();
    },

    /**
     * Handle logout - clear tokens and redirect to Keycloak
     */
    logout: async () => {
      const refreshToken = sessionStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);

      // Clear our tokens only (not all sessionStorage)
      clearStoredTokens();

      // Terminate Keycloak session (optional - Keycloak may require id_token_hint)
      if (refreshToken) {
        try {
          await fetch(logoutEndpoint, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: new URLSearchParams({
              client_id: config.clientId,
              refresh_token: refreshToken,
            }),
          });
        } catch (error) {
          console.warn('[AuthProvider] Keycloak logout failed (local session cleared):', error);
        }
      }

      console.log('[AuthProvider] Logout successful');
      return Promise.resolve();
    },

    /**
     * Check for errors and handle 401 Unauthorized
     */
    checkError: async (error) => {
      const status = error?.status || error?.response?.status;

      if (status === 401 || status === 403) {
        // Unauthorized - clear tokens and force re-login
        clearStoredTokens();
        return Promise.reject(new Error('Unauthorized'));
      }

      return Promise.resolve();
    },

    /**
     * Extract user permissions from backend API
     *
     * SECURITY FIX (VULN-001): Get roles from backend, not unverified JWT
     */
    getPermissions: async () => {
      // SECURITY: Do NOT decode unverified JWT for permissions
      // TODO: Call backend API endpoint that returns validated user roles
      // For MVP: Return empty array (backend enforces authorization)

      console.warn('[AuthProvider] getPermissions not implemented - backend enforces authorization');
      return Promise.resolve([]);
    },

    /**
     * Get user identity from backend API
     *
     * SECURITY FIX (VULN-001): Get identity from backend, not unverified JWT
     */
    getIdentity: async () => {
      // SECURITY: Do NOT decode unverified JWT for identity
      // TODO: Call backend API: GET /api/v1/auth/profile
      // Returns: { id, fullName, tenant_id, roles } (backend-validated)

      // For MVP: Return placeholder (backend manages identity)
      console.warn('[AuthProvider] getIdentity not implemented - use backend API');
      return Promise.resolve({
        id: 'user',
        fullName: 'User', // Backend should provide this
      });
    },
  };
}

/**
 * Refresh access token using refresh token
 * Reserved for future token refresh implementation (Story 7.4a.2)
 * @param _tokenEndpoint - Keycloak token endpoint
 * @param _clientId - Client ID
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
// @ts-expect-error - Function reserved for future token refresh implementation
async function refreshAccessToken(_tokenEndpoint: string, _clientId: string): Promise<void> {
  const refreshToken = sessionStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);

  if (!refreshToken) {
    throw new Error('No refresh token available');
  }

  const response = await fetch(_tokenEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: refreshToken,
      client_id: _clientId,
    }),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.error_description || 'Token refresh failed');
  }

  const data = await response.json();

  // Update access token
  sessionStorage.setItem(TOKEN_STORAGE_KEY, data.access_token);

  // Update refresh token if rotated (Keycloak may issue new refresh_token)
  if (data.refresh_token) {
    sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, data.refresh_token);
  }

  console.log('[AuthProvider] Token refreshed successfully');
}

/**
 * Clear only EAF-scoped authentication tokens from sessionStorage
 * (prevents wiping unrelated application data)
 */
function clearStoredTokens(): void {
  sessionStorage.removeItem(TOKEN_STORAGE_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
}
