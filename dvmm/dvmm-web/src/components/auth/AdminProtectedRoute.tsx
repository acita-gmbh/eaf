import { ShieldX } from 'lucide-react'
import { ProtectedRoute } from './ProtectedRoute'
import { Button } from '@/components/ui/button'
import { useNavigate } from 'react-router-dom'
import { useIsAdmin } from '@/hooks/useIsAdmin'
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
export function AdminProtectedRoute({ children }: Readonly<AdminProtectedRouteProps>) {
  const navigate = useNavigate()
  const isAdmin = useIsAdmin()

  return (
    <ProtectedRoute>
      {isAdmin ? (
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
function ForbiddenPage({ onGoBack }: Readonly<ForbiddenPageProps>) {
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
