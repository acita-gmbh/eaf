import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@/test/test-utils'
import { RequestCard } from './RequestCard'
import { type VmRequestSummary } from '@/api/vm-requests'

// Mock useCancelRequest hook
const mockMutate = vi.fn()
vi.mock('@/hooks/useCancelRequest', () => ({
  useCancelRequest: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
  })),
}))

// Mock sonner toast
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

import { useCancelRequest } from '@/hooks/useCancelRequest'
import { toast } from 'sonner'

const mockUseCancelRequest = vi.mocked(useCancelRequest)
const mockToast = vi.mocked(toast)

const createMockRequest = (overrides: Partial<VmRequestSummary> = {}): VmRequestSummary => ({
  id: 'req-123',
  requesterName: 'John Doe',
  projectId: 'proj-456',
  projectName: 'Test Project',
  vmName: 'web-server-01',
  size: 'M',
  cpuCores: 4,
  memoryGb: 16,
  diskGb: 100,
  justification: 'Test justification',
  status: 'PENDING',
  createdAt: '2024-01-15T10:30:00Z',
  updatedAt: '2024-01-15T10:30:00Z',
  ...overrides,
})

describe('RequestCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseCancelRequest.mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useCancelRequest>)
  })

  it('renders VM name', () => {
    render(<RequestCard request={createMockRequest()} />)
    expect(screen.getByTestId('request-vm-name')).toHaveTextContent('web-server-01')
  })

  it('renders project name', () => {
    render(<RequestCard request={createMockRequest()} />)
    expect(screen.getByTestId('request-project')).toHaveTextContent('Test Project')
  })

  it('renders CPU cores', () => {
    render(<RequestCard request={createMockRequest()} />)
    expect(screen.getByTestId('request-cpu')).toHaveTextContent('4 cores')
  })

  it('renders memory in GB', () => {
    render(<RequestCard request={createMockRequest()} />)
    expect(screen.getByTestId('request-memory')).toHaveTextContent('16 GB')
  })

  it('renders disk size in GB', () => {
    render(<RequestCard request={createMockRequest()} />)
    expect(screen.getByTestId('request-disk')).toHaveTextContent('100 GB')
  })

  it('renders creation date in German format', () => {
    render(<RequestCard request={createMockRequest()} />)
    // German format: DD.MM.YYYY, HH:MM
    expect(screen.getByTestId('request-created-at')).toHaveTextContent(/Created/)
  })

  it('renders data-testid with request id', () => {
    render(<RequestCard request={createMockRequest()} />)
    expect(screen.getByTestId('request-card-req-123')).toBeInTheDocument()
  })

  describe('status badge', () => {
    it('renders PENDING status badge', () => {
      render(<RequestCard request={createMockRequest({ status: 'PENDING' })} />)
      expect(screen.getByTestId('status-badge-pending')).toHaveTextContent('Pending')
    })

    it('renders APPROVED status badge', () => {
      render(<RequestCard request={createMockRequest({ status: 'APPROVED' })} />)
      expect(screen.getByTestId('status-badge-approved')).toHaveTextContent('Approved')
    })

    it('renders REJECTED status badge', () => {
      render(<RequestCard request={createMockRequest({ status: 'REJECTED' })} />)
      expect(screen.getByTestId('status-badge-rejected')).toHaveTextContent('Rejected')
    })

    it('renders CANCELLED status badge', () => {
      render(<RequestCard request={createMockRequest({ status: 'CANCELLED' })} />)
      expect(screen.getByTestId('status-badge-cancelled')).toHaveTextContent('Cancelled')
    })

    it('renders PROVISIONING status badge', () => {
      render(<RequestCard request={createMockRequest({ status: 'PROVISIONING' })} />)
      expect(screen.getByTestId('status-badge-provisioning')).toHaveTextContent('Provisioning')
    })

    it('renders READY status badge', () => {
      render(<RequestCard request={createMockRequest({ status: 'READY' })} />)
      expect(screen.getByTestId('status-badge-ready')).toHaveTextContent('Ready')
    })

    it('renders FAILED status badge', () => {
      render(<RequestCard request={createMockRequest({ status: 'FAILED' })} />)
      expect(screen.getByTestId('status-badge-failed')).toHaveTextContent('Failed')
    })
  })

  describe('cancel button visibility', () => {
    it('shows cancel button for PENDING requests', () => {
      render(<RequestCard request={createMockRequest({ status: 'PENDING' })} />)
      expect(screen.getByTestId('cancel-request-button')).toBeInTheDocument()
    })

    it('does not show cancel button for APPROVED requests', () => {
      render(<RequestCard request={createMockRequest({ status: 'APPROVED' })} />)
      expect(screen.queryByTestId('cancel-request-button')).not.toBeInTheDocument()
    })

    it('does not show cancel button for REJECTED requests', () => {
      render(<RequestCard request={createMockRequest({ status: 'REJECTED' })} />)
      expect(screen.queryByTestId('cancel-request-button')).not.toBeInTheDocument()
    })

    it('does not show cancel button for CANCELLED requests', () => {
      render(<RequestCard request={createMockRequest({ status: 'CANCELLED' })} />)
      expect(screen.queryByTestId('cancel-request-button')).not.toBeInTheDocument()
    })

    it('does not show cancel button for PROVISIONING requests', () => {
      render(<RequestCard request={createMockRequest({ status: 'PROVISIONING' })} />)
      expect(screen.queryByTestId('cancel-request-button')).not.toBeInTheDocument()
    })

    it('does not show cancel button for READY requests', () => {
      render(<RequestCard request={createMockRequest({ status: 'READY' })} />)
      expect(screen.queryByTestId('cancel-request-button')).not.toBeInTheDocument()
    })

    it('does not show cancel button for FAILED requests', () => {
      render(<RequestCard request={createMockRequest({ status: 'FAILED' })} />)
      expect(screen.queryByTestId('cancel-request-button')).not.toBeInTheDocument()
    })
  })

  describe('cancel flow', () => {
    it('opens confirmation dialog when cancel button is clicked', async () => {
      render(<RequestCard request={createMockRequest()} />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))

      await waitFor(() => {
        expect(screen.getByTestId('cancel-confirm-dialog')).toBeInTheDocument()
      })
    })

    it('displays VM name in confirmation dialog', async () => {
      render(<RequestCard request={createMockRequest({ vmName: 'my-test-vm' })} />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))

      await waitFor(() => {
        expect(screen.getByTestId('cancel-confirm-dialog')).toBeInTheDocument()
      })

      // VM name appears in the dialog description (inside <strong> element)
      expect(screen.getByRole('alertdialog')).toHaveTextContent('my-test-vm')
    })

    it('calls mutate with request id when confirmed', async () => {
      mockMutate.mockImplementation((_params, options) => {
        options?.onSuccess?.()
      })

      render(<RequestCard request={createMockRequest({ id: 'req-to-cancel' })} />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))

      await waitFor(() => {
        expect(screen.getByTestId('cancel-dialog-confirm')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      expect(mockMutate).toHaveBeenCalledWith(
        expect.objectContaining({ requestId: 'req-to-cancel' }),
        expect.any(Object)
      )
    })

    it('shows success toast on successful cancellation', async () => {
      mockMutate.mockImplementation((_params, options) => {
        options?.onSuccess?.()
      })

      render(<RequestCard request={createMockRequest()} />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))

      await waitFor(() => {
        expect(screen.getByTestId('cancel-dialog-confirm')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      expect(mockToast.success).toHaveBeenCalledWith('Request cancelled successfully')
    })

    it('closes dialog after successful cancellation', async () => {
      mockMutate.mockImplementation((_params, options) => {
        options?.onSuccess?.()
      })

      render(<RequestCard request={createMockRequest()} />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))

      await waitFor(() => {
        expect(screen.getByTestId('cancel-confirm-dialog')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      await waitFor(() => {
        expect(screen.queryByTestId('cancel-confirm-dialog')).not.toBeInTheDocument()
      })
    })
  })
})
