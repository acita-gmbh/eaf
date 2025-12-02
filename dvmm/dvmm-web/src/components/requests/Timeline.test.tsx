import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Timeline } from './Timeline'
import type { TimelineEvent } from '@/api/vm-requests'

// Mock date-utils to have consistent date formatting in tests
vi.mock('@/lib/date-utils', () => ({
  formatDateTime: vi.fn((date: string) => `Formatted: ${date}`),
}))

describe('Timeline', () => {
  const baseEvent: TimelineEvent = {
    eventType: 'CREATED',
    actorName: 'John Doe',
    details: null,
    occurredAt: '2025-01-15T10:30:00Z',
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('empty state', () => {
    it('shows empty message when no events provided', () => {
      render(<Timeline events={[]} />)

      expect(screen.getByText(/no timeline events available/i)).toBeInTheDocument()
    })
  })

  describe('event rendering', () => {
    it('renders a single CREATED event with correct label', () => {
      render(<Timeline events={[baseEvent]} />)

      expect(screen.getByTestId('request-timeline')).toBeInTheDocument()
      expect(screen.getByTestId('timeline-event-created')).toBeInTheDocument()
      expect(screen.getByTestId('timeline-event-label')).toHaveTextContent('Request Created')
    })

    it('renders actor name when present', () => {
      render(<Timeline events={[baseEvent]} />)

      expect(screen.getByTestId('timeline-event-actor')).toHaveTextContent('by John Doe')
    })

    it('does not render actor section when actorName is null', () => {
      const eventWithoutActor: TimelineEvent = {
        ...baseEvent,
        actorName: null,
      }

      render(<Timeline events={[eventWithoutActor]} />)

      expect(screen.queryByTestId('timeline-event-actor')).not.toBeInTheDocument()
    })

    it('renders formatted timestamp', () => {
      render(<Timeline events={[baseEvent]} />)

      const timeElement = screen.getByTestId('timeline-event-time')
      expect(timeElement).toHaveTextContent('Formatted: 2025-01-15T10:30:00Z')
      expect(timeElement).toHaveAttribute('datetime', '2025-01-15T10:30:00Z')
    })
  })

  describe('event types', () => {
    const eventTypes: Array<{ type: TimelineEvent['eventType']; expectedLabel: string }> = [
      { type: 'CREATED', expectedLabel: 'Request Created' },
      { type: 'APPROVED', expectedLabel: 'Approved' },
      { type: 'REJECTED', expectedLabel: 'Rejected' },
      { type: 'CANCELLED', expectedLabel: 'Cancelled' },
      { type: 'PROVISIONING_STARTED', expectedLabel: 'Provisioning Started' },
      { type: 'PROVISIONING_QUEUED', expectedLabel: 'Queued for Provisioning' },
      { type: 'VM_READY', expectedLabel: 'VM Ready' },
    ]

    eventTypes.forEach(({ type, expectedLabel }) => {
      it(`renders ${type} event with correct label`, () => {
        const event: TimelineEvent = { ...baseEvent, eventType: type }

        render(<Timeline events={[event]} />)

        expect(screen.getByTestId(`timeline-event-${type.toLowerCase()}`)).toBeInTheDocument()
        expect(screen.getByTestId('timeline-event-label')).toHaveTextContent(expectedLabel)
      })
    })
  })

  describe('event details parsing', () => {
    it('displays rejection reason from JSON details', () => {
      const rejectedEvent: TimelineEvent = {
        ...baseEvent,
        eventType: 'REJECTED',
        details: '{"reason": "Insufficient justification"}',
      }

      render(<Timeline events={[rejectedEvent]} />)

      expect(screen.getByTestId('timeline-event-reason')).toHaveTextContent(
        'Reason: Insufficient justification'
      )
    })

    it('displays cancellation reason from JSON details', () => {
      const cancelledEvent: TimelineEvent = {
        ...baseEvent,
        eventType: 'CANCELLED',
        details: '{"reason": "No longer needed"}',
      }

      render(<Timeline events={[cancelledEvent]} />)

      expect(screen.getByTestId('timeline-event-reason')).toHaveTextContent(
        'Reason: No longer needed'
      )
    })

    it('handles missing reason in details gracefully', () => {
      const eventWithEmptyDetails: TimelineEvent = {
        ...baseEvent,
        eventType: 'REJECTED',
        details: '{}',
      }

      render(<Timeline events={[eventWithEmptyDetails]} />)

      expect(screen.queryByTestId('timeline-event-reason')).not.toBeInTheDocument()
    })

    it('handles invalid JSON in details gracefully', () => {
      const eventWithInvalidJson: TimelineEvent = {
        ...baseEvent,
        eventType: 'REJECTED',
        details: 'not valid json',
      }

      render(<Timeline events={[eventWithInvalidJson]} />)

      expect(screen.queryByTestId('timeline-event-reason')).not.toBeInTheDocument()
    })

    it('handles null details gracefully', () => {
      const eventWithNullDetails: TimelineEvent = {
        ...baseEvent,
        details: null,
      }

      render(<Timeline events={[eventWithNullDetails]} />)

      expect(screen.queryByTestId('timeline-event-reason')).not.toBeInTheDocument()
    })
  })

  describe('multiple events', () => {
    it('renders multiple events in provided order', () => {
      const events: TimelineEvent[] = [
        { ...baseEvent, eventType: 'CREATED', occurredAt: '2025-01-15T10:00:00Z' },
        { ...baseEvent, eventType: 'APPROVED', occurredAt: '2025-01-15T11:00:00Z' },
        { ...baseEvent, eventType: 'VM_READY', occurredAt: '2025-01-15T12:00:00Z' },
      ]

      render(<Timeline events={events} />)

      const labels = screen.getAllByTestId('timeline-event-label')
      expect(labels).toHaveLength(3)
      expect(labels[0]).toHaveTextContent('Request Created')
      expect(labels[1]).toHaveTextContent('Approved')
      expect(labels[2]).toHaveTextContent('VM Ready')
    })

    it('renders all events correctly', () => {
      const events: TimelineEvent[] = [
        { ...baseEvent, eventType: 'CREATED' },
        { ...baseEvent, eventType: 'APPROVED' },
      ]

      render(<Timeline events={events} />)

      // Both events should be rendered
      expect(screen.getByTestId('timeline-event-created')).toBeInTheDocument()
      expect(screen.getByTestId('timeline-event-approved')).toBeInTheDocument()
    })
  })
})
