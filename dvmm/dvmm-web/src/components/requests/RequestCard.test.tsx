import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@/test/test-utils'
import { RequestCard } from './RequestCard'
import { ApiError, type VmRequestSummary } from '@/api/vm-requests'

// Mock useNavigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

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
    mockNavigate.mockClear()
    mockUseCancelRequest.mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useCancelRequest>)
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

  describe('navigation', () => {
    it('navigates to detail page when card is clicked', () => {
      render(<RequestCard request={createMockRequest({ id: 'req-123' })} />)

      fireEvent.click(screen.getByTestId('request-card-req-123'))

      expect(mockNavigate).toHaveBeenCalledWith('/requests/req-123')
    })

    it('navigates to detail page when Enter key is pressed', () => {
      render(<RequestCard request={createMockRequest({ id: 'req-456' })} />)

      fireEvent.keyDown(screen.getByTestId('request-card-req-456'), { key: 'Enter' })

      expect(mockNavigate).toHaveBeenCalledWith('/requests/req-456')
    })

    it('navigates to detail page when Space key is pressed', () => {
      render(<RequestCard request={createMockRequest({ id: 'req-789' })} />)

      fireEvent.keyDown(screen.getByTestId('request-card-req-789'), { key: ' ' })

      expect(mockNavigate).toHaveBeenCalledWith('/requests/req-789')
    })

    it('does not navigate when Cancel button is clicked', async () => {
      render(<RequestCard request={createMockRequest()} />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))

      await waitFor(() => {
        expect(screen.getByTestId('cancel-confirm-dialog')).toBeInTheDocument()
      })

      // Navigation should not have been triggered
      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('card has proper accessibility attributes', () => {
      render(<RequestCard request={createMockRequest({ vmName: 'test-vm' })} />)

      const card = screen.getByTestId('request-card-req-123')
      expect(card).toHaveAttribute('role', 'button')
      expect(card).toHaveAttribute('tabIndex', '0')
      expect(card).toHaveAttribute('aria-label', 'View details for test-vm')
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

    describe('error handling', () => {
      it('shows error toast for 404 not found', async () => {
        const notFoundError = new ApiError(404, 'Not Found', {
          type: 'not_found',
          message: 'Request not found',
        })
        mockMutate.mockImplementation((_params, options) => {
          options?.onError?.(notFoundError)
        })

        render(<RequestCard request={createMockRequest()} />)

        fireEvent.click(screen.getByTestId('cancel-request-button'))
        await waitFor(() => {
          expect(screen.getByTestId('cancel-dialog-confirm')).toBeInTheDocument()
        })

        fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

        expect(mockToast.error).toHaveBeenCalledWith('Request not found', {
          description: 'The request may have been deleted.',
        })
      })

      it('shows error toast for 403 forbidden', async () => {
        const forbiddenError = new ApiError(403, 'Forbidden', {
          type: 'forbidden',
          message: 'Access denied',
        })
        mockMutate.mockImplementation((_params, options) => {
          options?.onError?.(forbiddenError)
        })

        render(<RequestCard request={createMockRequest()} />)

        fireEvent.click(screen.getByTestId('cancel-request-button'))
        await waitFor(() => {
          expect(screen.getByTestId('cancel-dialog-confirm')).toBeInTheDocument()
        })

        fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

        expect(mockToast.error).toHaveBeenCalledWith('Not authorized', {
          description: 'You can only cancel your own requests.',
        })
      })

      it('shows error toast for 409 invalid state', async () => {
        const invalidStateError = new ApiError(409, 'Conflict', {
          type: 'invalid_state',
          message: 'Cannot cancel',
          currentState: 'APPROVED',
        })
        mockMutate.mockImplementation((_params, options) => {
          options?.onError?.(invalidStateError)
        })

        render(<RequestCard request={createMockRequest()} />)

        fireEvent.click(screen.getByTestId('cancel-request-button'))
        await waitFor(() => {
          expect(screen.getByTestId('cancel-dialog-confirm')).toBeInTheDocument()
        })

        fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

        // currentState.toLowerCase() = 'approved'
        expect(mockToast.error).toHaveBeenCalledWith('Cannot cancel request', {
          description: 'Request is approved and cannot be cancelled.',
        })
      })

      it('shows error toast for generic API error', async () => {
        const serverError = new ApiError(500, 'Server Error', {})
        mockMutate.mockImplementation((_params, options) => {
          options?.onError?.(serverError)
        })

        render(<RequestCard request={createMockRequest()} />)

        fireEvent.click(screen.getByTestId('cancel-request-button'))
        await waitFor(() => {
          expect(screen.getByTestId('cancel-dialog-confirm')).toBeInTheDocument()
        })

        fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

        // ApiError.message is 'API Error: 500 Server Error'
        expect(mockToast.error).toHaveBeenCalledWith('Failed to cancel request', {
          description: 'API Error: 500 Server Error',
        })
      })

      it('shows error toast for non-API error', async () => {
        const networkError = new Error('Network failure')
        mockMutate.mockImplementation((_params, options) => {
          options?.onError?.(networkError)
        })

        render(<RequestCard request={createMockRequest()} />)

        fireEvent.click(screen.getByTestId('cancel-request-button'))
        await waitFor(() => {
          expect(screen.getByTestId('cancel-dialog-confirm')).toBeInTheDocument()
        })

        fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

        expect(mockToast.error).toHaveBeenCalledWith('Failed to cancel request', {
          description: 'An unexpected error occurred.',
        })
      })
    })
  })

  describe('cancel button keyboard interaction', () => {
    it('opens dialog when Enter key is pressed on cancel button', async () => {
      render(<RequestCard request={createMockRequest()} />)

      const cancelButton = screen.getByTestId('cancel-request-button')
      fireEvent.keyDown(cancelButton, { key: 'Enter' })

      await waitFor(() => {
        expect(screen.getByTestId('cancel-confirm-dialog')).toBeInTheDocument()
      })

      // Should not navigate when opening dialog via keyboard
      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('opens dialog when Space key is pressed on cancel button', async () => {
      render(<RequestCard request={createMockRequest()} />)

      const cancelButton = screen.getByTestId('cancel-request-button')
      fireEvent.keyDown(cancelButton, { key: ' ' })

      await waitFor(() => {
        expect(screen.getByTestId('cancel-confirm-dialog')).toBeInTheDocument()
      })

      // Should not navigate when opening dialog via keyboard
      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('stops propagation for other keys on cancel button', () => {
      render(<RequestCard request={createMockRequest()} />)

      const cancelButton = screen.getByTestId('cancel-request-button')
      fireEvent.keyDown(cancelButton, { key: 'Tab' })

      // Dialog should not open for other keys
      expect(screen.queryByTestId('cancel-confirm-dialog')).not.toBeInTheDocument()
      // Card navigation should also not trigger
      expect(mockNavigate).not.toHaveBeenCalled()
    })
  })
})
