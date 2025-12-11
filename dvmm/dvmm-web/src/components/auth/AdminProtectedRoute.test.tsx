import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AdminProtectedRoute } from './AdminProtectedRoute'

// Mock react-oidc-context
const mockSigninRedirect = vi.fn()
let mockAuthState = {
  isAuthenticated: true,
  isLoading: false,
  error: null,
  user: {
    access_token: 'test-token',
    profile: {
      realm_access: {
        roles: ['admin', 'user'],
      },
    },
  },
  signinRedirect: mockSigninRedirect,
}

vi.mock('react-oidc-context', () => ({
  useAuth: () => mockAuthState,
}))

// Mock useNavigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

function renderWithRouter(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('AdminProtectedRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Reset to admin user by default
    mockAuthState = {
      isAuthenticated: true,
      isLoading: false,
      error: null,
      user: {
        access_token: 'test-token',
        profile: {
          realm_access: {
            roles: ['admin', 'user'],
          },
        },
      },
      signinRedirect: mockSigninRedirect,
    }
  })

  describe('admin role extraction', () => {
    it('renders children when user has admin role in realm_access.roles', () => {
      renderWithRouter(
        <AdminProtectedRoute>
          <div data-testid="admin-content">Admin Content</div>
        </AdminProtectedRoute>
      )

      expect(screen.getByTestId('admin-content')).toBeInTheDocument()
      expect(screen.getByText('Admin Content')).toBeInTheDocument()
    })

    it('shows forbidden page when user lacks admin role', () => {
      mockAuthState = {
        ...mockAuthState,
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['user'], // No admin role
            },
          },
        },
      }

      renderWithRouter(
        <AdminProtectedRoute>
          <div data-testid="admin-content">Admin Content</div>
        </AdminProtectedRoute>
      )

      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
      expect(screen.getByTestId('admin-forbidden')).toBeInTheDocument()
      expect(screen.getByText('Access Denied')).toBeInTheDocument()
    })

    it('shows forbidden page when realm_access.roles is empty', () => {
      mockAuthState = {
        ...mockAuthState,
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: [],
            },
          },
        },
      }

      renderWithRouter(
        <AdminProtectedRoute>
          <div data-testid="admin-content">Admin Content</div>
        </AdminProtectedRoute>
      )

      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
      expect(screen.getByTestId('admin-forbidden')).toBeInTheDocument()
    })

    it('shows forbidden page when realm_access is undefined', () => {
      mockAuthState = {
        ...mockAuthState,
        user: {
          access_token: 'test-token',
          profile: {},
        },
      }

      renderWithRouter(
        <AdminProtectedRoute>
          <div data-testid="admin-content">Admin Content</div>
        </AdminProtectedRoute>
      )

      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
      expect(screen.getByTestId('admin-forbidden')).toBeInTheDocument()
    })

    it('shows forbidden page when user is null', () => {
      mockAuthState = {
        ...mockAuthState,
        isAuthenticated: true,
        user: null as unknown as typeof mockAuthState.user,
      }

      renderWithRouter(
        <AdminProtectedRoute>
          <div data-testid="admin-content">Admin Content</div>
        </AdminProtectedRoute>
      )

      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
      expect(screen.getByTestId('admin-forbidden')).toBeInTheDocument()
    })
  })

  describe('forbidden page', () => {
    beforeEach(() => {
      // Set up non-admin user for forbidden page tests
      mockAuthState = {
        ...mockAuthState,
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['user'],
            },
          },
        },
      }
    })

    it('displays correct error message', () => {
      renderWithRouter(
        <AdminProtectedRoute>
          <div>Admin Content</div>
        </AdminProtectedRoute>
      )

      expect(screen.getByText('Access Denied')).toBeInTheDocument()
      expect(
        screen.getByText(/you don't have permission to access this page/i)
      ).toBeInTheDocument()
      expect(screen.getByText(/administrator privileges are required/i)).toBeInTheDocument()
    })

    it('has a button to return to dashboard', async () => {
      const user = userEvent.setup()

      renderWithRouter(
        <AdminProtectedRoute>
          <div>Admin Content</div>
        </AdminProtectedRoute>
      )

      const returnButton = screen.getByRole('button', { name: /return to dashboard/i })
      expect(returnButton).toBeInTheDocument()

      await user.click(returnButton)
      expect(mockNavigate).toHaveBeenCalledWith('/')
    })

    it('displays shield icon', () => {
      renderWithRouter(
        <AdminProtectedRoute>
          <div>Admin Content</div>
        </AdminProtectedRoute>
      )

      // Check for the SVG icon (ShieldX from lucide-react)
      const icon = document.querySelector('[aria-hidden="true"]')
      expect(icon).toBeInTheDocument()
    })
  })

  describe('authentication flow', () => {
    it('triggers signinRedirect when not authenticated (via ProtectedRoute)', () => {
      mockAuthState = {
        ...mockAuthState,
        isAuthenticated: false,
        isLoading: false,
        user: null as unknown as typeof mockAuthState.user,
      }

      renderWithRouter(
        <AdminProtectedRoute>
          <div data-testid="admin-content">Admin Content</div>
        </AdminProtectedRoute>
      )

      // ProtectedRoute handles redirect
      expect(mockSigninRedirect).toHaveBeenCalled()
      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
    })

    it('shows loading spinner when auth is loading (via ProtectedRoute)', () => {
      mockAuthState = {
        ...mockAuthState,
        isAuthenticated: false,
        isLoading: true,
      }

      renderWithRouter(
        <AdminProtectedRoute>
          <div data-testid="admin-content">Admin Content</div>
        </AdminProtectedRoute>
      )

      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
      const spinner = document.querySelector('.animate-spin')
      expect(spinner).toBeInTheDocument()
    })
  })

  describe('role case sensitivity', () => {
    it('matches admin role case-sensitively (lowercase)', () => {
      mockAuthState = {
        ...mockAuthState,
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['admin'], // lowercase
            },
          },
        },
      }

      renderWithRouter(
        <AdminProtectedRoute>
          <div data-testid="admin-content">Admin Content</div>
        </AdminProtectedRoute>
      )

      expect(screen.getByTestId('admin-content')).toBeInTheDocument()
    })

    it('does not match ADMIN (uppercase)', () => {
      mockAuthState = {
        ...mockAuthState,
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['ADMIN'], // uppercase - should not match
            },
          },
        },
      }

      renderWithRouter(
        <AdminProtectedRoute>
          <div data-testid="admin-content">Admin Content</div>
        </AdminProtectedRoute>
      )

      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
      expect(screen.getByTestId('admin-forbidden')).toBeInTheDocument()
    })
  })
})
