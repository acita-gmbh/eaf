import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StatsCard } from './StatsCard'
import { Clock } from 'lucide-react'

describe('StatsCard', () => {
  it('renders with placeholder value of 0', () => {
    render(
      <StatsCard
        title="Pending Requests"
        value={0}
        icon={<Clock data-testid="icon" />}
        variant="pending"
      />
    )

    expect(screen.getByText('0')).toBeInTheDocument()
    expect(screen.getByText('Pending Requests')).toBeInTheDocument()
  })

  it('applies pending variant styling', () => {
    render(
      <StatsCard
        title="Pending"
        value={0}
        icon={<Clock />}
        variant="pending"
      />
    )

    // Check that status-pending color classes are applied
    const valueElement = screen.getByText('0')
    expect(valueElement.className).toContain('text-[hsl(var(--status-pending))]')
  })

  it('applies approved variant styling', () => {
    render(
      <StatsCard
        title="Approved"
        value={5}
        icon={<Clock />}
        variant="approved"
      />
    )

    const valueElement = screen.getByText('5')
    expect(valueElement.className).toContain('text-[hsl(var(--status-approved))]')
  })

  it('applies info variant styling', () => {
    render(
      <StatsCard
        title="Provisioned"
        value={3}
        icon={<Clock />}
        variant="info"
      />
    )

    const valueElement = screen.getByText('3')
    expect(valueElement.className).toContain('text-[hsl(var(--status-info))]')
  })

  it('displays the provided title', () => {
    render(
      <StatsCard
        title="Custom Title"
        value={10}
        icon={<Clock />}
        variant="pending"
      />
    )

    expect(screen.getByText('Custom Title')).toBeInTheDocument()
  })
})
