import { useAuth } from 'react-oidc-context'
import { useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'

interface ProtectedRouteProps {
  children: ReactNode
}

export function ProtectedRoute({ children }: Readonly<ProtectedRouteProps>) {
  const auth = useAuth()
  const location = useLocation()

  // Show loading while checking auth
  if (auth.isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>
    )
  }

  // Redirect to login if not authenticated
  if (!auth.isAuthenticated) {
    console.info('[ProtectedRoute] User not authenticated, redirecting to login', {
      returnTo: location.pathname,
    })
    // Trigger Keycloak login
    auth.signinRedirect({ state: { returnTo: location.pathname } })
    return null
  }

  return <>{children}</>
}
