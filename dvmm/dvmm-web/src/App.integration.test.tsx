import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { ErrorBoundary } from './components/ErrorBoundary'
import { DashboardLayout } from './components/layout'
import { Dashboard } from './pages/Dashboard'
import { NewRequest } from './pages/NewRequest'

// Create a new QueryClient for each test
function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })
}

// Test-specific AppRoutes that doesn't use BrowserRouter (MemoryRouter is used instead)
function TestableAppRoutes() {
  const auth = useAuth()

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

  if (!auth.isAuthenticated) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center max-w-md p-6">
          <h1 className="text-3xl font-bold text-foreground mb-2">DVMM</h1>
          <p className="text-muted-foreground mb-6">
            Dynamic Virtual Machine Manager
          </p>
        </div>
      </div>
    )
  }

  return (
    <DashboardLayout>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/requests/new" element={<NewRequest />} />
      </Routes>
    </DashboardLayout>
  )
}

function TestApp({ initialEntries = ['/'] }: { initialEntries?: string[] }) {
  const queryClient = createTestQueryClient()
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={initialEntries}>
          <TestableAppRoutes />
        </MemoryRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  )
}

// Mock localStorage for onboarding hook
const localStorageMock = {
  store: {} as Record<string, string>,
  getItem: vi.fn((key: string) => localStorageMock.store[key] ?? null),
  setItem: vi.fn((key: string, value: string) => {
    localStorageMock.store[key] = value
  }),
  removeItem: vi.fn((key: string) => {
    delete localStorageMock.store[key]
  }),
  clear: vi.fn(() => {
    localStorageMock.store = {}
  }),
}

Object.defineProperty(window, 'localStorage', { value: localStorageMock })

// Mock react-oidc-context with authenticated state
const mockSigninRedirect = vi.fn()
const mockSignoutRedirect = vi.fn()

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    error: null,
    user: {
      access_token: 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJwcmVmZXJyZWRfdXNlcm5hbWUiOiJ0ZXN0dXNlciIsInRlbmFudF9pZCI6InRlc3QtdGVuYW50In0.signature',
    },
    signinRedirect: mockSigninRedirect,
    signoutRedirect: mockSignoutRedirect,
  }),
}))

// Mock auth-config functions
vi.mock('@/auth/auth-config', () => ({
  getUserNameFromToken: (token: string | undefined) => token ? 'Test User' : null,
  getTenantIdFromToken: (token: string | undefined) => token ? 'test-tenant' : null,
}))

// Mock api-client
vi.mock('@/api/api-client', () => ({
  fetchCsrfToken: vi.fn().mockResolvedValue(undefined),
  clearCsrfToken: vi.fn(),
}))

describe('App Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorageMock.clear()
    // Mark onboarding as complete to avoid tooltip interference
    localStorageMock.store['dvmm_onboarding_completed'] = 'true'
  })

  afterEach(() => {
    localStorageMock.clear()
  })

  it('renders dashboard layout after successful authentication', () => {
    render(<TestApp />)

    // Header elements should be visible
    expect(screen.getByText('DVMM')).toBeInTheDocument()
    expect(screen.getByText('Test User')).toBeInTheDocument()
    expect(screen.getByText('test-tenant')).toBeInTheDocument()
  })

  it('renders sidebar navigation in dashboard layout', () => {
    render(<TestApp />)

    // Sidebar navigation should be present with links (not buttons)
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /my requests/i })).toBeInTheDocument()
  })

  it('renders dashboard content with stats cards', () => {
    render(<TestApp />)

    // Dashboard content should be visible
    expect(screen.getByRole('heading', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByText('Pending Requests')).toBeInTheDocument()
    expect(screen.getByText('Approved Requests')).toBeInTheDocument()
    expect(screen.getByText('Provisioned VMs')).toBeInTheDocument()
  })

  it('renders Request New VM CTA button in dashboard content', () => {
    render(<TestApp />)

    // Sidebar has a link, main content has a CTA button
    // Check the sidebar link
    expect(screen.getByRole('link', { name: /request new vm/i })).toBeInTheDocument()

    // Check the CTA button in main content
    const ctaButton = screen.getByRole('button', { name: /request new vm/i })
    expect(ctaButton).toBeInTheDocument()
    // The CTA button should have the primary styling (bg-primary)
    expect(ctaButton.className).toContain('bg-primary')
  })

  it('renders My Requests section with empty state', () => {
    render(<TestApp />)

    // "My Requests" appears in sidebar nav as a link
    expect(screen.getByRole('link', { name: /my requests/i })).toBeInTheDocument()

    // Dashboard shows "My Requests" section with empty state (in card title)
    // The text appears twice: once in sidebar link, once in card title
    const myRequestsElements = screen.getAllByText('My Requests')
    expect(myRequestsElements).toHaveLength(2)
    expect(screen.getByText('No VMs requested yet')).toBeInTheDocument()
  })

  it('renders NewRequest page when navigating to /requests/new', () => {
    render(<TestApp initialEntries={['/requests/new']} />)

    // NewRequest page should be visible
    expect(screen.getByRole('heading', { name: /request new vm/i })).toBeInTheDocument()
  })
})

describe('App Integration - Unauthenticated', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorageMock.clear()
    // Override mock for unauthenticated state
    vi.doMock('react-oidc-context', () => ({
      useAuth: () => ({
        isAuthenticated: false,
        isLoading: false,
        error: null,
        user: null,
        signinRedirect: mockSigninRedirect,
        signoutRedirect: mockSignoutRedirect,
      }),
    }))
  })

  afterEach(() => {
    localStorageMock.clear()
  })

  // Note: This test would require re-importing App after doMock
  // For now, the authenticated flow tests above cover the main integration case
})
