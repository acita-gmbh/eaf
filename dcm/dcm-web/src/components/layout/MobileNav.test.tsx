import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { MobileNav } from './MobileNav'

/**
 * Mock useAuth from react-oidc-context.
 * Using vi.hoisted() to ensure mock is created before module import.
 */
const mockUseAuth = vi.hoisted(() =>
  vi.fn(() => ({
    user: null,
    isAuthenticated: false,
    isLoading: false,
  }))
)

vi.mock('react-oidc-context', () => ({
  useAuth: mockUseAuth,
}))

beforeEach(() => {
  // Reset to non-admin user by default
  mockUseAuth.mockReturnValue({
    user: null,
    isAuthenticated: true,
    isLoading: false,
  })
})

/**
 * Helper to render MobileNav with router context.
 */
function renderMobileNav(open: boolean) {
  return render(
    <MemoryRouter>
      <MobileNav open={open} onOpenChange={vi.fn()} />
    </MemoryRouter>
  )
}

describe('MobileNav', () => {
  it('renders sheet with sidebar content when open', () => {
    renderMobileNav(true)

    // Sheet should be open and show navigation
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
  })

  it('displays DCM title in sheet header', () => {
    renderMobileNav(true)

    expect(screen.getByText('DCM')).toBeInTheDocument()
  })

  it('shows navigation items as links', () => {
    renderMobileNav(true)

    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /my requests/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /request new vm/i })).toBeInTheDocument()
  })
})
