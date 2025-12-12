import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@/test/test-utils'
import { RequestsPlaceholder } from './RequestsPlaceholder'

describe('RequestsPlaceholder', () => {
  it('renders empty state title', () => {
    render(<RequestsPlaceholder />)
    expect(screen.getByText('No VMs requested yet')).toBeInTheDocument()
  })

  it('renders description', () => {
    render(<RequestsPlaceholder />)
    expect(
      screen.getByText('Request your first virtual machine')
    ).toBeInTheDocument()
  })

  it('renders CTA button with correct text', () => {
    render(<RequestsPlaceholder />)
    expect(
      screen.getByRole('button', { name: 'Request First VM' })
    ).toBeInTheDocument()
  })

  it('calls onRequestVm when CTA button is clicked', () => {
    const handleRequest = vi.fn()
    render(<RequestsPlaceholder onRequestVm={handleRequest} />)
    fireEvent.click(screen.getByRole('button', { name: 'Request First VM' }))
    expect(handleRequest).toHaveBeenCalledTimes(1)
  })

  it('has card header with My Requests title', () => {
    render(<RequestsPlaceholder />)
    expect(screen.getByText('My Requests')).toBeInTheDocument()
  })

  it('logs to console when CTA clicked without handler in DEV mode (fallback path)', () => {
    // Note: import.meta.env.DEV is true in test environment (Vitest)
    const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {})

    render(<RequestsPlaceholder />)
    fireEvent.click(screen.getByRole('button', { name: 'Request First VM' }))

    expect(consoleSpy).toHaveBeenCalledWith(
      '[RequestsPlaceholder] Navigate to VM request form'
    )

    consoleSpy.mockRestore()
  })
})
