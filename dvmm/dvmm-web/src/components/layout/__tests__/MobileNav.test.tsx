import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MobileNav } from '../MobileNav'

describe('MobileNav', () => {
  it('renders sheet with sidebar content when open', () => {
    render(
      <MobileNav
        open={true}
        onOpenChange={vi.fn()}
        currentPath="/"
      />
    )

    // Sheet should be open and show navigation
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
  })

  it('displays DVMM title in sheet header', () => {
    render(
      <MobileNav
        open={true}
        onOpenChange={vi.fn()}
        currentPath="/"
      />
    )

    expect(screen.getByText('DVMM')).toBeInTheDocument()
  })

  it('shows navigation items', () => {
    render(
      <MobileNav
        open={true}
        onOpenChange={vi.fn()}
        currentPath="/"
      />
    )

    expect(screen.getByRole('button', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /my requests/i })).toBeInTheDocument()
  })
})
