import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { Sidebar } from '../Sidebar'

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
 * Helper to render Sidebar with router context.
 */
function renderSidebar(initialPath: string = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Sidebar />
    </MemoryRouter>
  )
}

describe('Sidebar', () => {
  it('renders navigation items', () => {
    renderSidebar()

    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /my requests/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /request new vm/i })).toBeInTheDocument()
  })

  it('shows active state on current nav item', () => {
    renderSidebar('/')

    const dashboardLink = screen.getByRole('link', { name: /dashboard/i })
    expect(dashboardLink).toHaveAttribute('aria-current', 'page')
  })

  it('does not show active state on non-current nav items', () => {
    renderSidebar('/')

    const requestsLink = screen.getByRole('link', { name: /my requests/i })
    expect(requestsLink).not.toHaveAttribute('aria-current')
  })

  it('has correct navigation role and label', () => {
    renderSidebar()

    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
  })

  it('applies teal left border style to active item', () => {
    renderSidebar('/')

    const dashboardLink = screen.getByRole('link', { name: /dashboard/i })
    // Check that active styling classes are applied
    expect(dashboardLink).toHaveClass('border-l-2')
    expect(dashboardLink).toHaveClass('border-primary')
  })

  it('shows active state on requests page', () => {
    renderSidebar('/requests')

    const requestsLink = screen.getByRole('link', { name: /my requests/i })
    expect(requestsLink).toHaveAttribute('aria-current', 'page')
    expect(requestsLink).toHaveClass('border-l-2')
  })

  it('supports keyboard navigation through nav items', async () => {
    const user = userEvent.setup()
    renderSidebar()

    const dashboardLink = screen.getByRole('link', { name: /dashboard/i })
    const requestsLink = screen.getByRole('link', { name: /my requests/i })
    const newVmLink = screen.getByRole('link', { name: /request new vm/i })

    // Focus first item
    dashboardLink.focus()
    expect(document.activeElement).toBe(dashboardLink)

    // Tab to next item
    await user.tab()
    expect(document.activeElement).toBe(requestsLink)

    // Tab to next item
    await user.tab()
    expect(document.activeElement).toBe(newVmLink)
  })

  it('renders navigation links with correct hrefs', () => {
    renderSidebar()

    expect(screen.getByRole('link', { name: /dashboard/i })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: /my requests/i })).toHaveAttribute('href', '/requests')
    expect(screen.getByRole('link', { name: /request new vm/i })).toHaveAttribute('href', '/requests/new')
  })

  it('is hidden on mobile and visible on desktop via CSS classes', () => {
    render(
      <MemoryRouter>
        <Sidebar className="hidden md:block" />
      </MemoryRouter>
    )

    const sidebar = screen.getByRole('navigation', { name: /main navigation/i }).parentElement
    expect(sidebar).toHaveClass('hidden')
    expect(sidebar).toHaveClass('md:block')
  })
})
