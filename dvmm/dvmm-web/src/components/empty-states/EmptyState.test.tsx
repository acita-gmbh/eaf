import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@/test/test-utils'
import { FileQuestion } from 'lucide-react'
import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('renders title', () => {
    render(<EmptyState title="No items found" />)
    expect(screen.getByText('No items found')).toBeInTheDocument()
  })

  it('renders description when provided', () => {
    render(
      <EmptyState
        title="No items"
        description="Get started by adding your first item"
      />
    )
    expect(screen.getByText('Get started by adding your first item')).toBeInTheDocument()
  })

  it('renders icon when provided', () => {
    render(<EmptyState title="No items" icon={FileQuestion} />)
    // Lucide icons render as SVG elements
    const svg = document.querySelector('svg')
    expect(svg).toBeInTheDocument()
    expect(svg).toHaveAttribute('aria-hidden', 'true')
  })

  it('renders CTA button when label and handler provided', () => {
    const handleClick = vi.fn()
    render(
      <EmptyState
        title="No items"
        ctaLabel="Add first item"
        onCtaClick={handleClick}
      />
    )
    expect(screen.getByRole('button', { name: 'Add first item' })).toBeInTheDocument()
  })

  it('calls onCtaClick handler when CTA button is clicked', () => {
    const handleClick = vi.fn()
    render(
      <EmptyState
        title="No items"
        ctaLabel="Add first item"
        onCtaClick={handleClick}
      />
    )
    fireEvent.click(screen.getByRole('button', { name: 'Add first item' }))
    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('does not render CTA when only ctaLabel is provided without handler', () => {
    render(<EmptyState title="No items" ctaLabel="Add first item" />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('applies custom className', () => {
    const { container } = render(
      <EmptyState title="No items" className="custom-class" />
    )
    expect(container.firstChild).toHaveClass('custom-class')
  })
})
