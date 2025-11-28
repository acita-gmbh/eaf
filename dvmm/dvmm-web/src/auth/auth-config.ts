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
 * Extract tenant ID from JWT access token claims.
 */
export function getTenantIdFromToken(accessToken: string | undefined): string | null {
  if (!accessToken) return null

  try {
    const payload = accessToken.split(".")[1]
    const decoded = JSON.parse(atob(payload))
    return decoded.tenant_id ?? null
  } catch {
    return null
  }
}

/**
 * Extract user display name from JWT access token claims.
 */
export function getUserNameFromToken(accessToken: string | undefined): string | null {
  if (!accessToken) return null

  try {
    const payload = accessToken.split(".")[1]
    const decoded = JSON.parse(atob(payload))
    return decoded.name ?? decoded.preferred_username ?? null
  } catch {
    return null
  }
}
