import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import App from '../App'

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
    render(<App />)

    // Header elements should be visible
    expect(screen.getByText('DVMM')).toBeInTheDocument()
    expect(screen.getByText('Test User')).toBeInTheDocument()
    expect(screen.getByText('test-tenant')).toBeInTheDocument()
  })

  it('renders sidebar navigation in dashboard layout', () => {
    render(<App />)

    // Sidebar navigation should be present
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /my requests/i })).toBeInTheDocument()
  })

  it('renders dashboard content with stats cards', () => {
    render(<App />)

    // Dashboard content should be visible
    expect(screen.getByRole('heading', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByText('Pending Requests')).toBeInTheDocument()
    expect(screen.getByText('Approved Requests')).toBeInTheDocument()
    expect(screen.getByText('Provisioned VMs')).toBeInTheDocument()
  })

  it('renders Request New VM CTA button in dashboard content', () => {
    render(<App />)

    // There are two "Request New VM" buttons - one in sidebar nav and one CTA in main content
    // The CTA button has the "lg" size class (px-8 from h-10 size)
    const ctaButtons = screen.getAllByRole('button', { name: /request new vm/i })
    expect(ctaButtons.length).toBe(2) // One in sidebar, one CTA

    // The CTA button should have the primary styling (bg-primary)
    const ctaButton = ctaButtons.find(btn => btn.className.includes('bg-primary'))
    expect(ctaButton).toBeDefined()
  })

  it('renders My Requests section with German empty state', () => {
    render(<App />)

    // "My Requests" appears in both sidebar nav and as a section heading
    const myRequestsElements = screen.getAllByText('My Requests')
    expect(myRequestsElements.length).toBeGreaterThanOrEqual(1)

    // German empty state text (updated in Story 2.3)
    expect(screen.getByText('Noch keine VMs angefordert')).toBeInTheDocument()
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
