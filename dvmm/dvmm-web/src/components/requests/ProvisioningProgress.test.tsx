import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act } from '@/test/test-utils'
import { ProvisioningProgress } from './ProvisioningProgress'
import type { VmProvisioningStage } from '../../api/vm-requests'

describe('ProvisioningProgress', () => {
  const mockUpdatedAt = '2024-01-01T10:00:00Z'
  const mockStartedAt = '2024-01-01T09:55:00Z'

  beforeEach(() => {
    vi.useFakeTimers()
    // Set a fixed "now" time for consistent testing
    vi.setSystemTime(new Date('2024-01-01T10:00:30Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders the component title', () => {
    render(
      <ProvisioningProgress
        stage="CREATED"
        updatedAt={mockUpdatedAt}
        startedAt={mockStartedAt}
      />
    )

    expect(screen.getByText('Provisioning Progress')).toBeInTheDocument()
  })

  it('renders all provisioning stages', () => {
    render(
      <ProvisioningProgress
        stage="CLONING"
        updatedAt={mockUpdatedAt}
        startedAt={mockStartedAt}
      />
    )

    expect(screen.getByText('Request Created')).toBeInTheDocument()
    expect(screen.getByText('Cloning VM')).toBeInTheDocument()
    expect(screen.getByText('Configuring Hardware')).toBeInTheDocument()
    expect(screen.getByText('Powering On')).toBeInTheDocument()
    expect(screen.getByText('Waiting for Network')).toBeInTheDocument()
    expect(screen.getByText('Ready')).toBeInTheDocument()
  })

  describe('Stage indicators', () => {
    it('shows completed stages with checkmark icon', () => {
      const { container } = render(
        <ProvisioningProgress
          stage="CONFIGURING"
          updatedAt={mockUpdatedAt}
          startedAt={mockStartedAt}
        />
      )

      // CREATED and CLONING should be completed (index 0 and 1, current is 2)
      const checkIcons = container.querySelectorAll('.text-green-500')
      expect(checkIcons).toHaveLength(2)
    })

    it('shows current stage with spinner animation', () => {
      const { container } = render(
        <ProvisioningProgress
          stage="CLONING"
          updatedAt={mockUpdatedAt}
          startedAt={mockStartedAt}
        />
      )

      // Current stage should have animate-spin
      const spinners = container.querySelectorAll('.animate-spin')
      expect(spinners).toHaveLength(1)
    })

    it('shows pending stages with muted circle', () => {
      const { container } = render(
        <ProvisioningProgress
          stage="CREATED"
          updatedAt={mockUpdatedAt}
          startedAt={mockStartedAt}
        />
      )

      // All stages except CREATED should be pending (5 muted icons)
      const mutedIcons = container.querySelectorAll('.text-muted-foreground')
      // 5 pending stages + timestamp text elements
      expect(mutedIcons.length).toBeGreaterThanOrEqual(5)
    })

    it('highlights current stage label with font-medium', () => {
      render(
        <ProvisioningProgress
          stage="POWERING_ON"
          updatedAt={mockUpdatedAt}
          startedAt={mockStartedAt}
        />
      )

      const poweringOnLabel = screen.getByText('Powering On')
      expect(poweringOnLabel).toHaveClass('font-medium')
    })
  })

  describe('Timestamps', () => {
    it('shows timestamp for completed stages', () => {
      const { container } = render(
        <ProvisioningProgress
          stage="CONFIGURING"
          updatedAt="2024-01-01T14:30:45Z"
          startedAt={mockStartedAt}
        />
      )

      // Timestamps appear as text in the format HH:mm:ss
      // Check that timestamp elements exist for completed (2) + current (1) = 3 timestamps
      const timestampElements = container.querySelectorAll(
        '.text-xs.text-muted-foreground'
      )
      expect(timestampElements.length).toBe(3)
    })

    it('shows timestamp for current stage', () => {
      const { container } = render(
        <ProvisioningProgress
          stage="CLONING"
          updatedAt="2024-01-01T15:22:10Z"
          startedAt={mockStartedAt}
        />
      )

      // CREATED is completed (1) + CLONING is current (1) = 2 timestamps
      const timestampElements = container.querySelectorAll(
        '.text-xs.text-muted-foreground'
      )
      expect(timestampElements.length).toBe(2)
    })
  })

  describe('Timeout warning', () => {
    it('does not show warning when provisioning is within expected time', () => {
      // startedAt is 5 minutes ago (within 10 min threshold)
      vi.setSystemTime(new Date('2024-01-01T10:00:00Z'))

      render(
        <ProvisioningProgress
          stage="CLONING"
          updatedAt={mockUpdatedAt}
          startedAt="2024-01-01T09:55:00Z"
        />
      )

      expect(
        screen.queryByText(/Provisioning is taking longer than usual/)
      ).not.toBeInTheDocument()
    })

    it('shows warning when provisioning exceeds 10 minutes', () => {
      // startedAt is 11 minutes ago (exceeds 10 min threshold)
      vi.setSystemTime(new Date('2024-01-01T10:11:00Z'))

      render(
        <ProvisioningProgress
          stage="CLONING"
          updatedAt={mockUpdatedAt}
          startedAt="2024-01-01T10:00:00Z"
        />
      )

      expect(
        screen.getByText(/Provisioning is taking longer than usual/)
      ).toBeInTheDocument()
    })

    it('does not show warning when startedAt is not provided', () => {
      vi.setSystemTime(new Date('2024-01-01T10:15:00Z'))

      render(<ProvisioningProgress stage="CLONING" updatedAt={mockUpdatedAt} />)

      expect(
        screen.queryByText(/Provisioning is taking longer than usual/)
      ).not.toBeInTheDocument()
    })

    it('updates warning state when time passes threshold', async () => {
      // Start at 5 minutes elapsed
      vi.setSystemTime(new Date('2024-01-01T10:05:00Z'))

      render(
        <ProvisioningProgress
          stage="CLONING"
          updatedAt={mockUpdatedAt}
          startedAt="2024-01-01T10:00:00Z"
        />
      )

      // Initially no warning
      expect(
        screen.queryByText(/Provisioning is taking longer than usual/)
      ).not.toBeInTheDocument()

      // Advance time past threshold (10+ minutes) - wrap in act for React state update
      await act(async () => {
        vi.advanceTimersByTime(6 * 60 * 1000) // 6 more minutes = 11 total
      })

      // Warning should appear after interval update
      expect(
        screen.getByText(/Provisioning is taking longer than usual/)
      ).toBeInTheDocument()
    })
  })

  describe('Stage progression', () => {
    const stages: VmProvisioningStage[] = [
      'CREATED',
      'CLONING',
      'CONFIGURING',
      'POWERING_ON',
      'WAITING_FOR_NETWORK',
      'READY',
    ]

    stages.forEach((stage, index) => {
      it(`correctly renders stage ${stage} as current`, () => {
        const { container } = render(
          <ProvisioningProgress
            stage={stage}
            updatedAt={mockUpdatedAt}
            startedAt={mockStartedAt}
          />
        )

        // Number of completed stages should equal current index
        const checkIcons = container.querySelectorAll('.text-green-500')
        expect(checkIcons).toHaveLength(index)

        // Should have exactly one spinner (current stage) unless READY
        const spinners = container.querySelectorAll('.animate-spin')
        expect(spinners).toHaveLength(1)
      })
    })
  })

  describe('READY stage', () => {
    it('shows all stages as complete or current when READY', () => {
      const { container } = render(
        <ProvisioningProgress
          stage="READY"
          updatedAt={mockUpdatedAt}
          startedAt={mockStartedAt}
        />
      )

      // 5 completed stages (all except READY which is current)
      const checkIcons = container.querySelectorAll('.text-green-500')
      expect(checkIcons).toHaveLength(5)

      // READY stage shows as current with spinner
      const spinners = container.querySelectorAll('.animate-spin')
      expect(spinners).toHaveLength(1)
    })
  })
})
