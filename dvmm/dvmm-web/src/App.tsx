import { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { Button } from '@/components/ui/button'
import { getTenantIdFromToken, getUserNameFromToken } from '@/auth/auth-config'
import { fetchCsrfToken, clearCsrfToken } from '@/api/api-client'
import { LogOut, User, Building2 } from 'lucide-react'

function App() {
  const auth = useAuth()

  // Fetch CSRF token when authenticated
  useEffect(() => {
    if (auth.isAuthenticated && auth.user?.access_token) {
      fetchCsrfToken(auth.user.access_token).catch((error) => {
        console.error('Failed to fetch CSRF token:', error)
      })
    }
    return () => {
      if (!auth.isAuthenticated) {
        clearCsrfToken()
      }
    }
  }, [auth.isAuthenticated, auth.user?.access_token])

  // Handle OIDC callback processing
  if (auth.isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto mb-4" />
          <p className="text-muted-foreground">Loading...</p>
        </div>
      </div>
    )
  }

  // Handle authentication errors
  if (auth.error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center max-w-md p-6">
          <h1 className="text-2xl font-bold text-destructive mb-4">Authentication Error</h1>
          <p className="text-muted-foreground mb-6">{auth.error.message}</p>
          <Button onClick={() => auth.signinRedirect()}>
            Try Again
          </Button>
        </div>
      </div>
    )
  }

  // Redirect to Keycloak if not authenticated
  if (!auth.isAuthenticated) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center max-w-md p-6">
          <h1 className="text-3xl font-bold text-foreground mb-2">DVMM</h1>
          <p className="text-muted-foreground mb-6">
            Dynamic Virtual Machine Manager
          </p>
          <Button
            size="lg"
            onClick={() => auth.signinRedirect()}
            className="gap-2"
          >
            <User className="h-4 w-4" />
            Sign in with Keycloak
          </Button>
        </div>
      </div>
    )
  }

  // Authenticated - show dashboard
  const userName = getUserNameFromToken(auth.user?.access_token)
  const tenantId = getTenantIdFromToken(auth.user?.access_token)

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b bg-card">
        <div className="container mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <h1 className="text-xl font-bold text-primary">DVMM</h1>
          </div>

          <div className="flex items-center gap-4">
            {/* Tenant info */}
            {tenantId && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Building2 className="h-4 w-4" />
                <span>{tenantId}</span>
              </div>
            )}

            {/* User info */}
            <div className="flex items-center gap-2 text-sm">
              <User className="h-4 w-4 text-muted-foreground" />
              <span>{userName}</span>
            </div>

            {/* Logout button */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => auth.signoutRedirect()}
              className="gap-2"
            >
              <LogOut className="h-4 w-4" />
              Sign out
            </Button>
          </div>
        </div>
      </header>

      {/* Main content - Dashboard placeholder */}
      <main className="container mx-auto px-4 py-8">
        <div className="rounded-lg border bg-card p-6">
          <h2 className="text-2xl font-bold mb-4">My Virtual Machines</h2>
          <p className="text-muted-foreground">
            Welcome to DVMM! Your virtual machine requests will appear here.
          </p>
        </div>
      </main>
    </div>
  )
}

export default App
