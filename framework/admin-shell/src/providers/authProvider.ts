import { jwtDecode } from 'jwt-decode';
import type { AuthProvider } from 'react-admin';
import type { KeycloakConfig, JWTPayload } from '../types';
import { isTokenExpired, getTokenExpiresIn } from '../utils';

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
 * - JWT token storage in localStorage (MVP - see Security Considerations in story)
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

        // Store tokens in localStorage (SEC-001: XSS risk, see Security Considerations)
        localStorage.setItem('token', data.access_token);
        localStorage.setItem('refresh_token', data.refresh_token);

        console.log('[AuthProvider] Login successful', {
          expiresIn: data.expires_in,
        });

        return Promise.resolve();
      } catch (error: any) {
        console.error('[AuthProvider] Login failed:', error.message);
        return Promise.reject(error);
      }
    },

    /**
     * Check authentication status and auto-refresh token if needed
     */
    checkAuth: async () => {
      const token = localStorage.getItem('token');

      // No token = not authenticated
      if (!token) {
        return Promise.reject(new Error('Not authenticated'));
      }

      // Check if token expired
      if (isTokenExpired(token)) {
        console.warn('[AuthProvider] Token expired, clearing session');
        localStorage.clear();
        return Promise.reject(new Error('Token expired'));
      }

      // Auto-refresh if <5 minutes remaining (SEC-001: Short-lived tokens mitigation)
      const expiresIn = getTokenExpiresIn(token);
      if (expiresIn < 5 * 60 * 1000 && expiresIn > 0) {
        console.log('[AuthProvider] Token nearing expiration, auto-refreshing');

        try {
          await refreshAccessToken(tokenEndpoint, config.clientId);
        } catch (error) {
          // Refresh failed - logout user
          console.error('[AuthProvider] Token refresh failed:', error);
          localStorage.clear();
          return Promise.reject(new Error('Session expired, please re-login'));
        }
      }

      return Promise.resolve();
    },

    /**
     * Handle logout - clear tokens and redirect to Keycloak
     */
    logout: async () => {
      const refreshToken = localStorage.getItem('refresh_token');

      // Clear local storage first
      localStorage.clear();

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
        localStorage.clear();
        return Promise.reject(new Error('Unauthorized'));
      }

      return Promise.resolve();
    },

    /**
     * Extract user permissions from JWT roles
     */
    getPermissions: async () => {
      const token = localStorage.getItem('token');

      if (!token) {
        return Promise.reject(new Error('Not authenticated'));
      }

      try {
        const decoded = jwtDecode<JWTPayload>(token);
        const roles = decoded.realm_access?.roles || [];

        console.log('[AuthProvider] Permissions:', roles);
        return Promise.resolve(roles);
      } catch (error) {
        console.error('[AuthProvider] Failed to extract permissions:', error);
        return Promise.reject(error);
      }
    },

    /**
     * Get user identity from JWT
     */
    getIdentity: async () => {
      const token = localStorage.getItem('token');

      if (!token) {
        return Promise.reject(new Error('Not authenticated'));
      }

      try {
        const decoded = jwtDecode<JWTPayload>(token);

        return Promise.resolve({
          id: decoded.sub,
          fullName: decoded.sub, // Could extract from name claim if available
        });
      } catch (error) {
        return Promise.reject(error);
      }
    },
  };
}

/**
 * Refresh access token using refresh token
 * @param tokenEndpoint - Keycloak token endpoint
 * @param clientId - Client ID
 */
async function refreshAccessToken(tokenEndpoint: string, clientId: string): Promise<void> {
  const refreshToken = localStorage.getItem('refresh_token');

  if (!refreshToken) {
    throw new Error('No refresh token available');
  }

  const response = await fetch(tokenEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: refreshToken,
      client_id: clientId,
    }),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.error_description || 'Token refresh failed');
  }

  const data = await response.json();

  // Update access token (keep refresh token)
  localStorage.setItem('token', data.access_token);

  console.log('[AuthProvider] Token refreshed successfully');
}
