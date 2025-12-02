import { useAuth } from 'react-oidc-context'
import { ShieldX } from 'lucide-react'
import { ProtectedRoute } from './ProtectedRoute'
import { Button } from '@/components/ui/button'
import { useNavigate } from 'react-router-dom'
import type { ReactNode } from 'react'

interface AdminProtectedRouteProps {
  children: ReactNode
}

/**
 * Protects routes that require admin role.
 *
 * Story 2.9: Admin Approval Queue
 *
 * Wraps ProtectedRoute (ensures authentication) and adds admin role check.
 * Shows 403 Forbidden page for authenticated non-admin users.
 *
 * Note: Backend also enforces admin role via @PreAuthorize("hasRole('admin')"),
 * this is a UX improvement to show appropriate error before API call.
 */
export function AdminProtectedRoute({ children }: AdminProtectedRouteProps) {
  const auth = useAuth()
  const navigate = useNavigate()

  // Check for admin role in JWT claims
  // Keycloak stores roles in realm_access.roles or resource_access.[client].roles
  const hasAdminRole = (): boolean => {
    const user = auth.user
    if (!user) return false

    // Check realm_access.roles (typical Keycloak structure)
    const realmRoles = (user.profile as { realm_access?: { roles?: string[] } })?.realm_access?.roles
    if (realmRoles?.includes('admin')) {
      return true
    }

    // Could also check resource_access if needed
    return false
  }

  return (
    <ProtectedRoute>
      {hasAdminRole() ? (
        children
      ) : (
        <ForbiddenPage onGoBack={() => navigate('/')} />
      )}
    </ProtectedRoute>
  )
}

interface ForbiddenPageProps {
  onGoBack: () => void
}

/**
 * 403 Forbidden page for non-admin users.
 */
function ForbiddenPage({ onGoBack }: ForbiddenPageProps) {
  return (
    <div
      className="flex flex-col items-center justify-center py-16 text-center"
      data-testid="admin-forbidden"
    >
      <div className="p-4 rounded-full bg-destructive/10 mb-4">
        <ShieldX className="h-12 w-12 text-destructive" aria-hidden="true" />
      </div>
      <h1 className="text-2xl font-bold mb-2">Access Denied</h1>
      <p className="text-muted-foreground mb-6 max-w-sm">
        You don't have permission to access this page.
        Administrator privileges are required.
      </p>
      <Button onClick={onGoBack}>
        Return to Dashboard
      </Button>
    </div>
  )
}
