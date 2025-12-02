import { useEffect } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { Toaster } from 'sonner'
import { Button } from '@/components/ui/button'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { DashboardLayout } from '@/components/layout'
import { ProtectedRoute, AdminProtectedRoute } from '@/components/auth'
import { Dashboard } from '@/pages/Dashboard'
import { MyRequests } from '@/pages/MyRequests'
import { NewRequest } from '@/pages/NewRequest'
import { RequestDetail } from '@/pages/RequestDetail'
import { PendingRequests } from '@/pages/admin/PendingRequests'
import { fetchCsrfToken, clearCsrfToken } from '@/api/api-client'
import { queryClient } from '@/lib/query-client'
import { User } from 'lucide-react'

function AppRoutes() {
  const auth = useAuth()

  // Fetch CSRF token when authenticated, clear on logout
  useEffect(() => {
    if (auth.isAuthenticated && auth.user?.access_token) {
      fetchCsrfToken(auth.user.access_token).catch((error) => {
        console.error('Failed to fetch CSRF token:', error)
      })
    } else {
      // When not authenticated, ensure any CSRF token is cleared immediately
      clearCsrfToken()
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

  // Authenticated - render routes within DashboardLayout
  return (
    <DashboardLayout>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route
          path="/requests"
          element={
            <ProtectedRoute>
              <MyRequests />
            </ProtectedRoute>
          }
        />
        <Route
          path="/requests/new"
          element={
            <ProtectedRoute>
              <NewRequest />
            </ProtectedRoute>
          }
        />
        <Route
          path="/requests/:id"
          element={
            <ProtectedRoute>
              <RequestDetail />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/requests"
          element={
            <AdminProtectedRoute>
              <PendingRequests />
            </AdminProtectedRoute>
          }
        />
      </Routes>
    </DashboardLayout>
  )
}

function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
        <Toaster richColors position="top-right" />
      </QueryClientProvider>
    </ErrorBoundary>
  )
}

export default App
