import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { TooltipProvider } from '@/components/ui/tooltip'
import { PendingRequestsTable } from '../PendingRequestsTable'
import type { PendingRequest } from '@/api/admin'

// Mock react-router-dom navigation
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

function renderTable(requests: PendingRequest[], isLoading = false) {
  return render(
    <MemoryRouter>
      <TooltipProvider>
        <PendingRequestsTable requests={requests} isLoading={isLoading} />
      </TooltipProvider>
    </MemoryRouter>
  )
}

const createRequest = (overrides: Partial<PendingRequest> = {}): PendingRequest => ({
  id: 'req-1',
  requesterName: 'John Doe',
  vmName: 'test-vm',
  projectName: 'Test Project',
  size: 'M',
  cpuCores: 4,
  memoryGb: 8,
  diskGb: 100,
  createdAt: new Date().toISOString(),
  ...overrides,
})

describe('PendingRequestsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('rendering', () => {
    it('renders table with correct headers', () => {
      renderTable([createRequest()])

      expect(screen.getByRole('columnheader', { name: /requester/i })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: /vm name/i })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: /project/i })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: /size/i })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: /age/i })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: /actions/i })).toBeInTheDocument()
    })

    it('renders request data correctly', () => {
      const request = createRequest({
        requesterName: 'Jane Smith',
        vmName: 'prod-server',
        projectName: 'Production',
        size: 'L',
      })
      renderTable([request])

      expect(screen.getByTestId('requester-name')).toHaveTextContent('Jane Smith')
      expect(screen.getByTestId('vm-name')).toHaveTextContent('prod-server')
      expect(screen.getByTestId('project-name')).toHaveTextContent('Production')
      expect(screen.getByTestId('size')).toHaveTextContent('L')
    })

    it('renders multiple requests', () => {
      const requests = [
        createRequest({ id: 'req-1', vmName: 'vm-1' }),
        createRequest({ id: 'req-2', vmName: 'vm-2' }),
        createRequest({ id: 'req-3', vmName: 'vm-3' }),
      ]
      renderTable(requests)

      expect(screen.getByTestId('pending-request-row-req-1')).toBeInTheDocument()
      expect(screen.getByTestId('pending-request-row-req-2')).toBeInTheDocument()
      expect(screen.getByTestId('pending-request-row-req-3')).toBeInTheDocument()
    })

    it('renders empty table when no requests', () => {
      renderTable([])

      expect(screen.getByTestId('pending-requests-table')).toBeInTheDocument()
      expect(screen.queryByTestId(/pending-request-row/)).not.toBeInTheDocument()
    })
  })

  describe('age highlighting (48h threshold)', () => {
    it('does not highlight requests less than 48 hours old', () => {
      // 24 hours ago
      const recentDate = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()
      const request = createRequest({ createdAt: recentDate })
      renderTable([request])

      const row = screen.getByTestId('pending-request-row-req-1')
      expect(row).not.toHaveClass('bg-amber-50')
      expect(screen.queryByTestId('waiting-long-badge')).not.toBeInTheDocument()
    })

    it('highlights requests older than 48 hours with amber background', () => {
      // 72 hours ago
      const oldDate = new Date(Date.now() - 72 * 60 * 60 * 1000).toISOString()
      const request = createRequest({ createdAt: oldDate })
      renderTable([request])

      const row = screen.getByTestId('pending-request-row-req-1')
      expect(row).toHaveClass('bg-amber-50')
    })

    it('shows "Waiting long" badge for requests older than 48 hours', () => {
      // 72 hours ago
      const oldDate = new Date(Date.now() - 72 * 60 * 60 * 1000).toISOString()
      const request = createRequest({ createdAt: oldDate })
      renderTable([request])

      expect(screen.getByTestId('waiting-long-badge')).toBeInTheDocument()
      expect(screen.getByTestId('waiting-long-badge')).toHaveTextContent('Waiting long')
    })

    it('does not highlight requests exactly 48 hours old', () => {
      // Exactly 48 hours ago
      const exactlyThreshold = new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString()
      const request = createRequest({ createdAt: exactlyThreshold })
      renderTable([request])

      const row = screen.getByTestId('pending-request-row-req-1')
      // At exactly 48h, differenceInHours returns 48, and check is > 48, so no highlight
      expect(row).not.toHaveClass('bg-amber-50')
    })
  })

  describe('navigation', () => {
    it('navigates to request detail on row click', async () => {
      const user = userEvent.setup()
      const request = createRequest({ id: 'req-123' })
      renderTable([request])

      const row = screen.getByTestId('pending-request-row-req-123')
      await user.click(row)

      expect(mockNavigate).toHaveBeenCalledWith('/requests/req-123')
    })

    it('navigates on Enter key press', async () => {
      const user = userEvent.setup()
      const request = createRequest({ id: 'req-456' })
      renderTable([request])

      const row = screen.getByTestId('pending-request-row-req-456')
      row.focus()
      await user.keyboard('{Enter}')

      expect(mockNavigate).toHaveBeenCalledWith('/requests/req-456')
    })

    it('navigates on Space key press', async () => {
      const user = userEvent.setup()
      const request = createRequest({ id: 'req-789' })
      renderTable([request])

      const row = screen.getByTestId('pending-request-row-req-789')
      row.focus()
      await user.keyboard(' ')

      expect(mockNavigate).toHaveBeenCalledWith('/requests/req-789')
    })

    it('has correct aria attributes for keyboard navigation', () => {
      const request = createRequest()
      renderTable([request])

      const row = screen.getByTestId('pending-request-row-req-1')
      expect(row).toHaveAttribute('role', 'button')
      expect(row).toHaveAttribute('tabIndex', '0')
      expect(row).toHaveAttribute('aria-label', expect.stringContaining('test-vm'))
    })
  })

  describe('action buttons', () => {
    it('renders disabled approve and reject buttons', () => {
      const request = createRequest()
      renderTable([request])

      const approveButton = screen.getByTestId('approve-button')
      const rejectButton = screen.getByTestId('reject-button')

      expect(approveButton).toBeDisabled()
      expect(rejectButton).toBeDisabled()
    })

    it('action button clicks do not trigger row navigation', async () => {
      const user = userEvent.setup()
      const request = createRequest({ id: 'req-action' })
      renderTable([request])

      // Click on the approve button wrapper (since button is disabled)
      const approveButton = screen.getByTestId('approve-button')
      await user.click(approveButton)

      // Navigation should not have been triggered
      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('buttons have correct accessibility attributes', () => {
      const request = createRequest()
      renderTable([request])

      const approveButton = screen.getByTestId('approve-button')
      const rejectButton = screen.getByTestId('reject-button')

      expect(approveButton).toHaveAttribute('aria-disabled', 'true')
      expect(approveButton).toHaveAttribute('aria-label', expect.stringContaining('Approve'))
      expect(rejectButton).toHaveAttribute('aria-disabled', 'true')
      expect(rejectButton).toHaveAttribute('aria-label', expect.stringContaining('Reject'))
    })
  })

  describe('loading state', () => {
    it('renders skeleton loader when loading', () => {
      renderTable([], true)

      expect(screen.getByTestId('pending-requests-table-skeleton')).toBeInTheDocument()
      expect(screen.queryByTestId('pending-requests-table')).not.toBeInTheDocument()
    })

    it('skeleton has 5 rows', () => {
      renderTable([], true)

      const skeleton = screen.getByTestId('pending-requests-table-skeleton')
      const rows = within(skeleton).getAllByRole('row')
      // 1 header row + 5 skeleton body rows = 6 rows
      expect(rows.length).toBe(6)
    })

    it('does not render skeleton when not loading', () => {
      renderTable([createRequest()])

      expect(screen.queryByTestId('pending-requests-table-skeleton')).not.toBeInTheDocument()
      expect(screen.getByTestId('pending-requests-table')).toBeInTheDocument()
    })
  })

  describe('size display', () => {
    it('displays size code', () => {
      const request = createRequest({ size: 'XL' })
      renderTable([request])

      expect(screen.getByTestId('size')).toHaveTextContent('XL')
    })
  })
})
