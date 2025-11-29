import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Sidebar } from '../Sidebar'

describe('Sidebar', () => {
  it('renders navigation items', () => {
    render(<Sidebar />)

    expect(screen.getByRole('button', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /my requests/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /request new vm/i })).toBeInTheDocument()
  })

  it('shows active state on current nav item', () => {
    render(<Sidebar currentPath="/" />)

    const dashboardButton = screen.getByRole('button', { name: /dashboard/i })
    expect(dashboardButton).toHaveAttribute('aria-current', 'page')
  })

  it('does not show active state on non-current nav items', () => {
    render(<Sidebar currentPath="/" />)

    const requestsButton = screen.getByRole('button', { name: /my requests/i })
    expect(requestsButton).not.toHaveAttribute('aria-current')
  })

  it('has correct navigation role and label', () => {
    render(<Sidebar />)

    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
  })

  it('applies teal left border style to active item', () => {
    render(<Sidebar currentPath="/" />)

    const dashboardButton = screen.getByRole('button', { name: /dashboard/i })
    // Check that active styling classes are applied
    expect(dashboardButton.className).toContain('border-l-2')
    expect(dashboardButton.className).toContain('border-primary')
  })

  it('supports keyboard navigation through nav items', async () => {
    const user = userEvent.setup()
    render(<Sidebar />)

    const dashboardButton = screen.getByRole('button', { name: /dashboard/i })
    const requestsButton = screen.getByRole('button', { name: /my requests/i })
    const newVmButton = screen.getByRole('button', { name: /request new vm/i })

    // Focus first item
    dashboardButton.focus()
    expect(document.activeElement).toBe(dashboardButton)

    // Tab to next item
    await user.tab()
    expect(document.activeElement).toBe(requestsButton)

    // Tab to next item
    await user.tab()
    expect(document.activeElement).toBe(newVmButton)
  })

  it('is hidden on mobile and visible on desktop via CSS classes', () => {
    render(<Sidebar className="hidden md:block" />)

    const sidebar = screen.getByRole('navigation', { name: /main navigation/i }).parentElement
    expect(sidebar?.className).toContain('hidden')
    expect(sidebar?.className).toContain('md:block')
  })
})
