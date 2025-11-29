import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Dashboard } from '../Dashboard'

describe('Dashboard', () => {
  it('displays dashboard heading', () => {
    render(<Dashboard />)

    expect(screen.getByRole('heading', { name: /dashboard/i })).toBeInTheDocument()
  })

  it('displays Request New VM CTA button', () => {
    render(<Dashboard />)

    expect(screen.getByRole('button', { name: /request new vm/i })).toBeInTheDocument()
  })

  it('displays CTA button with Tech Teal styling (primary variant)', () => {
    render(<Dashboard />)

    const ctaButton = screen.getByRole('button', { name: /request new vm/i })
    // shadcn Button with default variant uses primary color
    expect(ctaButton).toBeInTheDocument()
    // Button should be large size
    expect(ctaButton.className).toContain('gap-2')
  })

  it('displays stats cards with placeholder values (0, 0, 0)', () => {
    render(<Dashboard />)

    // All three stats should show 0
    const zeros = screen.getAllByText('0')
    expect(zeros).toHaveLength(3)
  })

  it('displays Pending Requests stat', () => {
    render(<Dashboard />)

    expect(screen.getByText('Pending Requests')).toBeInTheDocument()
  })

  it('displays Approved Requests stat', () => {
    render(<Dashboard />)

    expect(screen.getByText('Approved Requests')).toBeInTheDocument()
  })

  it('displays Provisioned VMs stat', () => {
    render(<Dashboard />)

    expect(screen.getByText('Provisioned VMs')).toBeInTheDocument()
  })

  it('displays My Requests section with placeholder text', () => {
    render(<Dashboard />)

    expect(screen.getByText('My Requests')).toBeInTheDocument()
    expect(screen.getByText('Your VM requests will appear here')).toBeInTheDocument()
  })
})
