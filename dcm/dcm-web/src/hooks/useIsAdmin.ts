import { useAuth } from 'react-oidc-context'

/**
 * JWT profile structure for Keycloak tokens.
 *
 * Keycloak stores realm-level roles in realm_access.roles.
 * Client-specific roles would be in resource_access.[client].roles.
 */
interface KeycloakProfile {
  realm_access?: {
    roles?: string[]
  }
}

/**
 * Checks if the current user has admin role.
 *
 * Story 2.9: Admin Approval Queue
 *
 * Extracts roles from Keycloak JWT claims (realm_access.roles).
 * Used to conditionally show admin-only UI elements and protect admin routes.
 *
 * @returns true if user has 'admin' role, false otherwise
 *
 * @example
 * ```tsx
 * function AdminFeature() {
 *   const isAdmin = useIsAdmin()
 *   if (!isAdmin) return null
 *   return <AdminPanel />
 * }
 * ```
 */
export function useIsAdmin(): boolean {
  const auth = useAuth()
  const user = auth.user

  if (!user) return false

  const profile = user.profile as KeycloakProfile | undefined
  const realmRoles = profile?.realm_access?.roles

  return realmRoles?.includes('admin') ?? false
}
