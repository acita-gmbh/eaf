import type { AuthProviderProps } from "react-oidc-context"

/**
 * Keycloak OIDC configuration for react-oidc-context.
 *
 * Environment variables:
 * - VITE_KEYCLOAK_URL: Keycloak base URL (e.g., http://localhost:8180)
 * - VITE_KEYCLOAK_REALM: Keycloak realm name (e.g., dvmm)
 * - VITE_KEYCLOAK_CLIENT_ID: OIDC client ID (e.g., dvmm-web)
 */
export const oidcConfig: AuthProviderProps = {
  authority: `${import.meta.env.VITE_KEYCLOAK_URL}/realms/${import.meta.env.VITE_KEYCLOAK_REALM}`,
  client_id: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  scope: "openid profile email",
  automaticSilentRenew: true,
  onSigninCallback: () => {
    // Remove OIDC state from URL after successful login
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}

/**
 * Decode base64url-encoded string to standard base64 for atob.
 *
 * JWTs use base64url encoding (RFC 4648 §5):
 * - Uses '-' instead of '+'
 * - Uses '_' instead of '/'
 * - No padding required
 *
 * @param base64url - Base64url encoded string
 * @returns Standard base64 encoded string suitable for atob()
 */
function base64urlToBase64(base64url: string): string {
  // Replace base64url chars with standard base64 chars
  let base64 = base64url.replace(/-/g, '+').replace(/_/g, '/')
  // Add padding if needed
  const padding = base64.length % 4
  if (padding) {
    base64 += '='.repeat(4 - padding)
  }
  return base64
}

/**
 * Parse JWT payload claims.
 *
 * @param accessToken - JWT access token
 * @returns Parsed payload claims or null if parsing fails
 */
function parseJwtPayload(accessToken: string | undefined): Record<string, unknown> | null {
  if (!accessToken) return null

  try {
    const payload = accessToken.split(".")[1]
    if (!payload) return null
    const base64 = base64urlToBase64(payload)
    return JSON.parse(atob(base64))
  } catch {
    return null
  }
}

/**
 * Extract tenant ID from JWT access token claims.
 */
export function getTenantIdFromToken(accessToken: string | undefined): string | null {
  const claims = parseJwtPayload(accessToken)
  if (!claims) return null
  return typeof claims.tenant_id === 'string' ? claims.tenant_id : null
}

/**
 * Extract user display name from JWT access token claims.
 */
export function getUserNameFromToken(accessToken: string | undefined): string | null {
  const claims = parseJwtPayload(accessToken)
  if (!claims) return null
  if (typeof claims.name === 'string') return claims.name
  if (typeof claims.preferred_username === 'string') return claims.preferred_username
  return null
}
