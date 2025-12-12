import { describe, it, expect } from 'vitest'
import { render, screen } from '@/test/test-utils'
import { AdminQueueEmptyState } from './AdminQueueEmptyState'

describe('AdminQueueEmptyState', () => {
  it('renders main message', () => {
    render(<AdminQueueEmptyState />)
    expect(screen.getByText('No pending approvals')).toBeInTheDocument()
  })

  it('renders positive description', () => {
    render(<AdminQueueEmptyState />)
    expect(screen.getByText('All requests have been processed')).toBeInTheDocument()
  })

  it('renders with check circle icon (emerald color)', () => {
    render(<AdminQueueEmptyState />)
    // Icon should be present and have aria-hidden
    const svg = document.querySelector('svg')
    expect(svg).toBeInTheDocument()
    expect(svg).toHaveAttribute('aria-hidden', 'true')
  })

  it('applies custom className', () => {
    const { container } = render(<AdminQueueEmptyState className="custom-class" />)
    expect(container.firstChild).toHaveClass('custom-class')
  })
})
