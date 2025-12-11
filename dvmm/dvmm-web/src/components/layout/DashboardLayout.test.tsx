import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DashboardLayout } from './DashboardLayout'

// Mock child components to isolate DashboardLayout testing
vi.mock('./Header', () => ({
  Header: ({ onMobileMenuToggle }: { onMobileMenuToggle?: () => void }) => (
    <header data-testid="mock-header">
      <button onClick={onMobileMenuToggle} data-testid="mobile-menu-button">
        Menu
      </button>
    </header>
  ),
}))

vi.mock('./Sidebar', () => ({
  Sidebar: ({ className }: { className?: string }) => (
    <aside data-testid="mock-sidebar" className={className}>
      Sidebar
    </aside>
  ),
}))

vi.mock('./MobileNav', () => ({
  MobileNav: ({
    open,
    onOpenChange,
  }: {
    open: boolean
    onOpenChange: (open: boolean) => void
  }) => (
    <div data-testid="mock-mobile-nav" data-open={open}>
      <button onClick={() => onOpenChange(false)} data-testid="close-mobile-nav">
        Close
      </button>
    </div>
  ),
}))

describe('DashboardLayout', () => {
  it('renders children in main content area', () => {
    render(
      <DashboardLayout>
        <div data-testid="test-child">Child Content</div>
      </DashboardLayout>
    )

    expect(screen.getByTestId('test-child')).toBeInTheDocument()
    expect(screen.getByText('Child Content')).toBeInTheDocument()
  })

  it('renders header, sidebar, and mobile nav components', () => {
    render(
      <DashboardLayout>
        <div>Content</div>
      </DashboardLayout>
    )

    expect(screen.getByTestId('mock-header')).toBeInTheDocument()
    expect(screen.getByTestId('mock-sidebar')).toBeInTheDocument()
    expect(screen.getByTestId('mock-mobile-nav')).toBeInTheDocument()
  })

  it('opens mobile nav when header menu button is clicked', async () => {
    const user = userEvent.setup()
    render(
      <DashboardLayout>
        <div>Content</div>
      </DashboardLayout>
    )

    const mobileNav = screen.getByTestId('mock-mobile-nav')
    expect(mobileNav).toHaveAttribute('data-open', 'false')

    const menuButton = screen.getByTestId('mobile-menu-button')
    await user.click(menuButton)

    expect(mobileNav).toHaveAttribute('data-open', 'true')
  })

  it('closes mobile nav when onOpenChange is called with false', async () => {
    const user = userEvent.setup()
    render(
      <DashboardLayout>
        <div>Content</div>
      </DashboardLayout>
    )

    // First open the nav
    const menuButton = screen.getByTestId('mobile-menu-button')
    await user.click(menuButton)

    const mobileNav = screen.getByTestId('mock-mobile-nav')
    expect(mobileNav).toHaveAttribute('data-open', 'true')

    // Close it
    const closeButton = screen.getByTestId('close-mobile-nav')
    await user.click(closeButton)

    expect(mobileNav).toHaveAttribute('data-open', 'false')
  })

  it('applies correct responsive classes to sidebar', () => {
    render(
      <DashboardLayout>
        <div>Content</div>
      </DashboardLayout>
    )

    const sidebar = screen.getByTestId('mock-sidebar')
    expect(sidebar.className).toContain('hidden')
    expect(sidebar.className).toContain('md:block')
  })

  it('wraps content in main element with correct styling', () => {
    render(
      <DashboardLayout>
        <div>Content</div>
      </DashboardLayout>
    )

    const main = screen.getByRole('main')
    expect(main).toBeInTheDocument()
    expect(main.className).toContain('flex-1')
    expect(main.className).toContain('p-6')
  })
})
