import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Header } from './Header'

// Mock signoutRedirect to capture calls
const mockSignoutRedirect = vi.fn()

// Mock react-oidc-context
vi.mock('react-oidc-context', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    error: null,
    user: {
      // Use simple placeholder instead of JWT-shaped token to avoid gitleaks false positives
      access_token: 'test-access-token',
    },
    signinRedirect: vi.fn(),
    signoutRedirect: mockSignoutRedirect,
  }),
}))

// Mock auth-config functions
vi.mock('@/auth/auth-config', () => ({
  getUserNameFromToken: (token: string | undefined) => token ? 'Test User' : null,
  getTenantIdFromToken: (token: string | undefined) => token ? 'test-tenant' : null,
}))

describe('Header', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('displays user name from auth context', () => {
    render(<Header />)

    expect(screen.getByText('Test User')).toBeInTheDocument()
  })

  it('displays tenant info when available', () => {
    render(<Header />)

    expect(screen.getByText('test-tenant')).toBeInTheDocument()
  })

  it('displays DVMM logo', () => {
    render(<Header />)

    expect(screen.getByText('DVMM')).toBeInTheDocument()
  })

  it('displays user avatar with initials', () => {
    render(<Header />)

    // Avatar fallback should show initials
    expect(screen.getByText('TU')).toBeInTheDocument() // "Test User" => "TU"
  })

  it('displays sign out button', () => {
    render(<Header />)

    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument()
  })

  it('displays mobile menu button with correct aria-label', () => {
    render(<Header />)

    expect(screen.getByRole('button', { name: /toggle navigation menu/i })).toBeInTheDocument()
  })

  it('calls onMobileMenuToggle when hamburger button is clicked', async () => {
    const onMobileMenuToggle = vi.fn()
    const user = userEvent.setup()

    render(<Header onMobileMenuToggle={onMobileMenuToggle} />)

    const menuButton = screen.getByRole('button', { name: /toggle navigation menu/i })
    await user.click(menuButton)

    expect(onMobileMenuToggle).toHaveBeenCalledTimes(1)
  })

  it('calls signoutRedirect when sign out button is clicked', async () => {
    const user = userEvent.setup()

    render(<Header />)

    const signOutButton = screen.getByRole('button', { name: /sign out/i })
    await user.click(signOutButton)

    expect(mockSignoutRedirect).toHaveBeenCalledTimes(1)
  })
})
