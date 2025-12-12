import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { VmDetailsCard } from './VmDetailsCard'
import type { VmRuntimeDetails } from '@/api/vm-requests'

// Mock clipboard API
const mockClipboard = {
  writeText: vi.fn().mockResolvedValue(undefined),
}
Object.assign(navigator, { clipboard: mockClipboard })

describe('VmDetailsCard', () => {
  const fullVmDetails: VmRuntimeDetails = {
    vmwareVmId: 'vm-123',
    ipAddress: '192.168.1.100',
    hostname: 'web-server-01.local',
    powerState: 'POWERED_ON',
    guestOs: 'Ubuntu 22.04.3 LTS (64-bit)',
    lastSyncedAt: '2024-01-15T10:30:00Z',
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('power state display', () => {
    it('shows "Running" badge when powered on', () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      const badge = screen.getByTestId('vm-power-state')
      expect(badge).toHaveTextContent('Running')
    })

    it('shows "Powered Off" badge when powered off', () => {
      render(
        <VmDetailsCard
          vmDetails={{ ...fullVmDetails, powerState: 'POWERED_OFF' }}
        />
      )

      const badge = screen.getByTestId('vm-power-state')
      expect(badge).toHaveTextContent('Powered Off')
    })

    it('shows "Suspended" badge when suspended', () => {
      render(
        <VmDetailsCard
          vmDetails={{ ...fullVmDetails, powerState: 'SUSPENDED' }}
        />
      )

      const badge = screen.getByTestId('vm-power-state')
      expect(badge).toHaveTextContent('Suspended')
    })

    it('shows unknown state when power state is null', () => {
      render(
        <VmDetailsCard vmDetails={{ ...fullVmDetails, powerState: null }} />
      )

      const badge = screen.getByTestId('vm-power-state')
      expect(badge).toHaveTextContent('Unknown')
    })
  })

  describe('IP address display', () => {
    it('shows IP address when available', () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      expect(screen.getByTestId('vm-ip-address')).toHaveTextContent(
        '192.168.1.100'
      )
    })

    it('shows "Not detected" when IP is null', () => {
      render(
        <VmDetailsCard vmDetails={{ ...fullVmDetails, ipAddress: null }} />
      )

      expect(screen.getByText('Not detected')).toBeInTheDocument()
    })

    it('copies IP address to clipboard when copy button clicked', async () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      const copyButton = screen.getByLabelText('Copy IP address')
      fireEvent.click(copyButton)

      expect(mockClipboard.writeText).toHaveBeenCalledWith('192.168.1.100')
    })
  })

  describe('hostname display', () => {
    it('shows hostname when available', () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      expect(screen.getByTestId('vm-hostname')).toHaveTextContent(
        'web-server-01.local'
      )
    })

    it('shows "Not detected" when hostname is null', () => {
      render(
        <VmDetailsCard vmDetails={{ ...fullVmDetails, hostname: null }} />
      )

      // Two "Not detected" - one for hostname, potentially one for IP if both null
      const notDetectedElements = screen.getAllByText('Not detected')
      expect(notDetectedElements.length).toBeGreaterThanOrEqual(1)
    })
  })

  describe('guest OS display', () => {
    it('shows guest OS when available', () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      expect(screen.getByTestId('vm-guest-os')).toHaveTextContent(
        'Ubuntu 22.04.3 LTS (64-bit)'
      )
    })

    it('does not show guest OS section when null', () => {
      render(
        <VmDetailsCard vmDetails={{ ...fullVmDetails, guestOs: null }} />
      )

      expect(screen.queryByTestId('vm-guest-os')).not.toBeInTheDocument()
    })
  })

  describe('SSH instructions', () => {
    it('shows SSH command when VM is powered on with IP', () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      expect(screen.getByTestId('vm-connection-command')).toHaveTextContent(
        'ssh <username>@192.168.1.100'
      )
    })

    it('does not show SSH command when VM is powered off', () => {
      render(
        <VmDetailsCard
          vmDetails={{ ...fullVmDetails, powerState: 'POWERED_OFF' }}
        />
      )

      expect(screen.queryByTestId('vm-connection-command')).not.toBeInTheDocument()
    })

    it('does not show SSH command when IP is null', () => {
      render(
        <VmDetailsCard vmDetails={{ ...fullVmDetails, ipAddress: null }} />
      )

      expect(screen.queryByTestId('vm-connection-command')).not.toBeInTheDocument()
    })

    it('copies SSH command to clipboard when copy button clicked', async () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      // Find the Copy button inside the SSH section (not the IP copy icon button)
      const sshSection = screen.getByTestId('vm-connection-command').parentElement!
      const copyButton = sshSection.querySelector('button')!
      fireEvent.click(copyButton)

      expect(mockClipboard.writeText).toHaveBeenCalledWith(
        'ssh <username>@192.168.1.100'
      )
    })
  })

  describe('last synced display', () => {
    it('shows last synced timestamp when available', () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      expect(screen.getByTestId('vm-last-synced')).toBeInTheDocument()
    })

    it('does not show last synced when null', () => {
      render(
        <VmDetailsCard
          vmDetails={{ ...fullVmDetails, lastSyncedAt: null }}
        />
      )

      expect(screen.queryByTestId('vm-last-synced')).not.toBeInTheDocument()
    })
  })

  describe('refresh button', () => {
    it('shows refresh button when onRefresh is provided', () => {
      const onRefresh = vi.fn()
      render(<VmDetailsCard vmDetails={fullVmDetails} onRefresh={onRefresh} />)

      expect(
        screen.getByTestId('refresh-vm-status-button')
      ).toBeInTheDocument()
    })

    it('does not show refresh button when onRefresh is not provided', () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      expect(
        screen.queryByTestId('refresh-vm-status-button')
      ).not.toBeInTheDocument()
    })

    it('calls onRefresh when refresh button clicked', () => {
      const onRefresh = vi.fn()
      render(<VmDetailsCard vmDetails={fullVmDetails} onRefresh={onRefresh} />)

      const refreshButton = screen.getByTestId('refresh-vm-status-button')
      fireEvent.click(refreshButton)

      expect(onRefresh).toHaveBeenCalledTimes(1)
    })

    it('disables refresh button when isRefreshing is true', () => {
      const onRefresh = vi.fn()
      render(
        <VmDetailsCard
          vmDetails={fullVmDetails}
          onRefresh={onRefresh}
          isRefreshing={true}
        />
      )

      const refreshButton = screen.getByTestId('refresh-vm-status-button')
      expect(refreshButton).toBeDisabled()
    })

    it('shows spinning animation when isRefreshing is true', () => {
      const onRefresh = vi.fn()
      render(
        <VmDetailsCard
          vmDetails={fullVmDetails}
          onRefresh={onRefresh}
          isRefreshing={true}
        />
      )

      const refreshButton = screen.getByTestId('refresh-vm-status-button')
      const icon = refreshButton.querySelector('svg')
      expect(icon).toHaveClass('animate-spin')
    })
  })

  describe('card rendering', () => {
    it('renders the card with proper test id', () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      expect(screen.getByTestId('vm-details-card')).toBeInTheDocument()
    })

    it('shows "VM Details" title', () => {
      render(<VmDetailsCard vmDetails={fullVmDetails} />)

      expect(screen.getByText('VM Details')).toBeInTheDocument()
    })
  })

  describe('minimal VM details', () => {
    it('handles VM with only power state', () => {
      const minimalVm: VmRuntimeDetails = {
        vmwareVmId: 'vm-456',
        ipAddress: null,
        hostname: null,
        powerState: 'POWERED_ON',
        guestOs: null,
        lastSyncedAt: null,
      }

      render(<VmDetailsCard vmDetails={minimalVm} />)

      expect(screen.getByTestId('vm-power-state')).toHaveTextContent('Running')
      expect(screen.queryByTestId('vm-guest-os')).not.toBeInTheDocument()
      expect(screen.queryByTestId('vm-connection-command')).not.toBeInTheDocument()
    })
  })
})
