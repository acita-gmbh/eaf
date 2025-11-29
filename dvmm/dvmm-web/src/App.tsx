import { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { Button } from '@/components/ui/button'
import { DashboardLayout } from '@/components/layout'
import { Dashboard } from '@/pages/Dashboard'
import { fetchCsrfToken, clearCsrfToken } from '@/api/api-client'
import { User } from 'lucide-react'

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

  // Authenticated - show dashboard with layout
  return (
    <DashboardLayout>
      <Dashboard />
    </DashboardLayout>
  )
}

export default App
