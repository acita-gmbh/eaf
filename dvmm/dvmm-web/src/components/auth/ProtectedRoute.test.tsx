import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'

// Mock react-oidc-context
const mockSigninRedirect = vi.fn()
let mockAuthState = {
  isAuthenticated: true,
  isLoading: false,
  error: null,
  user: { access_token: 'test-token' },
  signinRedirect: mockSigninRedirect,
}

vi.mock('react-oidc-context', () => ({
  useAuth: () => mockAuthState,
}))

function renderWithRouter(ui: React.ReactElement) {
  return render(
    <MemoryRouter>
      {ui}
    </MemoryRouter>
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAuthState = {
      isAuthenticated: true,
      isLoading: false,
      error: null,
      user: { access_token: 'test-token' },
      signinRedirect: mockSigninRedirect,
    }
  })

  it('renders children when authenticated', () => {
    renderWithRouter(
      <ProtectedRoute>
        <div data-testid="protected-content">Protected Content</div>
      </ProtectedRoute>
    )

    expect(screen.getByTestId('protected-content')).toBeInTheDocument()
    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })

  it('shows loading spinner when auth is loading', () => {
    mockAuthState = {
      ...mockAuthState,
      isAuthenticated: false,
      isLoading: true,
    }

    renderWithRouter(
      <ProtectedRoute>
        <div data-testid="protected-content">Protected Content</div>
      </ProtectedRoute>
    )

    // Should show spinner, not content
    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument()
    // Spinner has animate-spin class
    const spinner = document.querySelector('.animate-spin')
    expect(spinner).toBeInTheDocument()
  })

  it('triggers signinRedirect when not authenticated', () => {
    mockAuthState = {
      ...mockAuthState,
      isAuthenticated: false,
      isLoading: false,
    }

    renderWithRouter(
      <ProtectedRoute>
        <div data-testid="protected-content">Protected Content</div>
      </ProtectedRoute>
    )

    // Should not render content
    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument()
    // Should call signinRedirect
    expect(mockSigninRedirect).toHaveBeenCalledWith({ state: { returnTo: '/' } })
  })
})
