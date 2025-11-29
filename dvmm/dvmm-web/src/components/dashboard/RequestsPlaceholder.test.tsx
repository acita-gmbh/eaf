import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@/test/test-utils'
import { RequestsPlaceholder } from './RequestsPlaceholder'

describe('RequestsPlaceholder', () => {
  it('renders German empty state title', () => {
    render(<RequestsPlaceholder />)
    expect(screen.getByText('Noch keine VMs angefordert')).toBeInTheDocument()
  })

  it('renders German description', () => {
    render(<RequestsPlaceholder />)
    expect(
      screen.getByText('Fordern Sie Ihre erste virtuelle Maschine an')
    ).toBeInTheDocument()
  })

  it('renders CTA button with correct text', () => {
    render(<RequestsPlaceholder />)
    expect(
      screen.getByRole('button', { name: 'Erste VM anfordern' })
    ).toBeInTheDocument()
  })

  it('calls onRequestVm when CTA button is clicked', () => {
    const handleRequest = vi.fn()
    render(<RequestsPlaceholder onRequestVm={handleRequest} />)
    fireEvent.click(screen.getByRole('button', { name: 'Erste VM anfordern' }))
    expect(handleRequest).toHaveBeenCalledTimes(1)
  })

  it('has card header with My Requests title', () => {
    render(<RequestsPlaceholder />)
    expect(screen.getByText('My Requests')).toBeInTheDocument()
  })
})
