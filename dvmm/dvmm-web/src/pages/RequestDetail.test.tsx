import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@/test/test-utils'
import userEvent from '@testing-library/user-event'
import { RequestDetail } from './RequestDetail'
import { ApiError } from '@/api/vm-requests'

// Mock useCancelRequest hook - match RequestCard pattern exactly
const mockCancelMutate = vi.fn()
vi.mock('@/hooks/useCancelRequest', () => ({
  useCancelRequest: vi.fn(() => ({
    mutate: mockCancelMutate,
    isPending: false,
  })),
}))

// Hoisted mocks for useRequestDetail
const mockRefetch = vi.hoisted(() => vi.fn())
const mockNavigate = vi.hoisted(() => vi.fn())

const mockUseRequestDetail = vi.hoisted(() =>
  vi.fn(() => ({
    data: null,
    isLoading: true,
    isError: false,
    error: null,
    refetch: mockRefetch,
  }))
)

vi.mock('@/hooks/useRequestDetail', () => ({
  useRequestDetail: mockUseRequestDetail,
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useParams: () => ({ id: 'test-request-id' }),
    useNavigate: () => mockNavigate,
  }
})

// Mock sonner toast
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

// Mock react-oidc-context for useProvisioningProgress hook
vi.mock('react-oidc-context', () => ({
  useAuth: vi.fn(() => ({
    user: { access_token: 'test-token' },
    isAuthenticated: true,
  })),
}))

import { useCancelRequest } from '@/hooks/useCancelRequest'
import { toast } from 'sonner'

const mockUseCancelRequest = vi.mocked(useCancelRequest)
const mockToast = vi.mocked(toast)

const mockRequestData = {
  id: 'test-request-id',
  vmName: 'web-server-01',
  projectName: 'Project Alpha',
  status: 'PENDING' as const,
  size: {
    code: 'MEDIUM',
    cpuCores: 4,
    memoryGb: 16,
    diskGb: 100,
  },
  justification: 'Development server for the team',
  requesterName: 'John Doe',
  createdAt: '2024-01-01T10:00:00Z',
  timeline: [
    {
      eventType: 'CREATED' as const,
      actorName: 'John Doe',
      details: null,
      occurredAt: '2024-01-01T10:00:00Z',
    },
  ],
}

describe('RequestDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseCancelRequest.mockReturnValue({
      mutate: mockCancelMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useCancelRequest>)
  })

  describe('Loading State', () => {
    it('shows loading spinner', () => {
      mockUseRequestDetail.mockReturnValue({
        data: null,
        isLoading: true,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      expect(screen.getByTestId('request-detail-loading')).toBeInTheDocument()
      expect(document.querySelector('.animate-spin')).toBeInTheDocument()
    })
  })

  describe('Error States', () => {
    it('shows not found error for 404', () => {
      mockUseRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 404, message: 'Not found' },
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      expect(screen.getByTestId('request-detail-not-found')).toBeInTheDocument()
      expect(screen.getByText('Request Not Found')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /view my requests/i })).toBeInTheDocument()
    })

    it('shows general error for other errors', () => {
      mockUseRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 500, message: 'Server error' },
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      expect(screen.getByTestId('request-detail-error')).toBeInTheDocument()
      expect(screen.getByText('Error Loading Request')).toBeInTheDocument()
      expect(screen.getByText('Server error')).toBeInTheDocument()
    })

    it('shows default error message when error has no message', () => {
      mockUseRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 500 },
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      expect(screen.getByText('Could not load request details.')).toBeInTheDocument()
    })

    it('calls refetch on Try Again click', async () => {
      const refetchMock = vi.fn()
      mockUseRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 500 },
        refetch: refetchMock,
      })

      const user = userEvent.setup()
      render(<RequestDetail />)

      await user.click(screen.getByRole('button', { name: /try again/i }))

      expect(refetchMock).toHaveBeenCalled()
    })
  })

  describe('Success State', () => {
    beforeEach(() => {
      mockUseRequestDetail.mockReturnValue({
        data: mockRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })
    })

    it('displays request details', () => {
      render(<RequestDetail />)

      expect(screen.getByTestId('request-detail-page')).toBeInTheDocument()
      expect(screen.getByTestId('request-detail-vm-name')).toHaveTextContent('web-server-01')
      expect(screen.getByTestId('request-detail-project')).toHaveTextContent('Project Alpha')
    })

    it('displays VM specifications', () => {
      render(<RequestDetail />)

      expect(screen.getByTestId('request-detail-cpu')).toHaveTextContent('4 cores')
      expect(screen.getByTestId('request-detail-memory')).toHaveTextContent('16 GB')
      expect(screen.getByTestId('request-detail-disk')).toHaveTextContent('100 GB')
    })

    it('displays justification', () => {
      render(<RequestDetail />)

      expect(screen.getByTestId('request-detail-justification')).toHaveTextContent(
        'Development server for the team'
      )
    })

    it('displays requester and creation date', () => {
      render(<RequestDetail />)

      expect(screen.getByTestId('request-detail-requester')).toHaveTextContent('John Doe')
      expect(screen.getByTestId('request-detail-created')).toBeInTheDocument()
    })

    it('shows cancel button for PENDING status', () => {
      render(<RequestDetail />)

      expect(screen.getByTestId('cancel-request-button')).toBeInTheDocument()
    })

    it('hides cancel button for non-PENDING status', () => {
      mockUseRequestDetail.mockReturnValue({
        data: { ...mockRequestData, status: 'APPROVED' },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      expect(screen.queryByTestId('cancel-request-button')).not.toBeInTheDocument()
    })

    it('displays timeline section', () => {
      render(<RequestDetail />)

      expect(screen.getByText('Timeline')).toBeInTheDocument()
    })
  })

  describe('Cancel Request Flow', () => {
    it('opens cancel dialog when button is clicked', async () => {
      mockUseRequestDetail.mockReturnValue({
        data: mockRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))

      await waitFor(() => {
        expect(screen.getByRole('alertdialog')).toBeInTheDocument()
      })
    })

    it('shows success toast and refetches on successful cancel', async () => {
      const refetchMock = vi.fn()
      mockUseRequestDetail.mockReturnValue({
        data: mockRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: refetchMock,
      })

      mockCancelMutate.mockImplementation((_params, options) => {
        options?.onSuccess?.()
      })

      render(<RequestDetail />)

      // Use fireEvent like RequestCard.test.tsx for Radix AlertDialog compatibility
      fireEvent.click(screen.getByTestId('cancel-request-button'))

      await waitFor(() => {
        expect(screen.getByTestId('cancel-dialog-confirm')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      await waitFor(() => {
        expect(mockCancelMutate).toHaveBeenCalled()
      })
      expect(mockToast.success).toHaveBeenCalledWith('Request cancelled successfully')
      expect(refetchMock).toHaveBeenCalled()
    })

    it('shows error toast for 404 not found', async () => {
      mockUseRequestDetail.mockReturnValue({
        data: mockRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const notFoundError = new ApiError(404, 'Not Found', {
        type: 'not_found',
        message: 'Request not found',
      })
      mockCancelMutate.mockImplementation((_params, options) => {
        options?.onError?.(notFoundError)
      })

      render(<RequestDetail />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))
      await waitFor(() => {
        expect(screen.getByRole('alertdialog')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      await waitFor(() => {
        expect(mockToast.error).toHaveBeenCalledWith('Request not found', {
          description: 'The request may have been deleted.',
        })
      })
    })

    it('shows error toast for 403 forbidden', async () => {
      mockUseRequestDetail.mockReturnValue({
        data: mockRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const forbiddenError = new ApiError(403, 'Forbidden', {
        type: 'forbidden',
        message: 'Access denied',
      })
      mockCancelMutate.mockImplementation((_params, options) => {
        options?.onError?.(forbiddenError)
      })

      render(<RequestDetail />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))
      await waitFor(() => {
        expect(screen.getByRole('alertdialog')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      await waitFor(() => {
        expect(mockToast.error).toHaveBeenCalledWith('Not authorized', {
          description: 'You can only cancel your own requests.',
        })
      })
    })

    it('shows error toast for 409 invalid state', async () => {
      mockUseRequestDetail.mockReturnValue({
        data: mockRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const invalidStateError = new ApiError(409, 'Conflict', {
        type: 'invalid_state',
        message: 'Cannot cancel',
        currentState: 'APPROVED',
      })
      mockCancelMutate.mockImplementation((_params, options) => {
        options?.onError?.(invalidStateError)
      })

      render(<RequestDetail />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))
      await waitFor(() => {
        expect(screen.getByRole('alertdialog')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      // currentState.toLowerCase() = 'approved'
      await waitFor(() => {
        expect(mockToast.error).toHaveBeenCalledWith('Cannot cancel request', {
          description: 'Request is approved and cannot be cancelled.',
        })
      })
    })

    it('shows error toast for generic API error', async () => {
      mockUseRequestDetail.mockReturnValue({
        data: mockRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const serverError = new ApiError(500, 'Server Error', {})
      mockCancelMutate.mockImplementation((_params, options) => {
        options?.onError?.(serverError)
      })

      render(<RequestDetail />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))
      await waitFor(() => {
        expect(screen.getByRole('alertdialog')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      // ApiError.message is 'API Error: 500 Server Error'
      await waitFor(() => {
        expect(mockToast.error).toHaveBeenCalledWith('Failed to cancel request', {
          description: 'API Error: 500 Server Error',
        })
      })
    })

    it('shows error toast for non-API error', async () => {
      mockUseRequestDetail.mockReturnValue({
        data: mockRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const networkError = new Error('Network failure')
      mockCancelMutate.mockImplementation((_params, options) => {
        options?.onError?.(networkError)
      })

      render(<RequestDetail />)

      fireEvent.click(screen.getByTestId('cancel-request-button'))
      await waitFor(() => {
        expect(screen.getByRole('alertdialog')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      await waitFor(() => {
        expect(mockToast.error).toHaveBeenCalledWith('Failed to cancel request', {
          description: 'An unexpected error occurred.',
        })
      })
    })

    // Note: The early return path for undefined id is covered by integration tests.
    // Testing it in isolation would require resetting vi.mock() which isn't possible per test.
  })

  describe('Navigation', () => {
    it('navigates back when back button is clicked', async () => {
      mockUseRequestDetail.mockReturnValue({
        data: mockRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const user = userEvent.setup()
      render(<RequestDetail />)

      const backButton = screen.getByRole('button', { name: /back to my requests/i })
      await user.click(backButton)

      expect(mockNavigate).toHaveBeenCalledWith('/requests')
    })
  })

  describe('Null data handling', () => {
    it('returns null when data is null after loading', () => {
      mockUseRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const { container } = render(<RequestDetail />)

      // Should return null (empty container)
      expect(container.firstChild).toBeNull()
    })
  })

  describe('Failed Status Display (AC-3.6.3)', () => {
    const failedRequestData = {
      ...mockRequestData,
      status: 'FAILED' as const,
      timeline: [
        {
          eventType: 'CREATED' as const,
          actorName: 'John Doe',
          details: null,
          occurredAt: '2024-01-01T10:00:00Z',
        },
        {
          eventType: 'APPROVED' as const,
          actorName: 'Admin User',
          details: null,
          occurredAt: '2024-01-01T11:00:00Z',
        },
        {
          eventType: 'PROVISIONING_STARTED' as const,
          actorName: 'System',
          details: null,
          occurredAt: '2024-01-01T12:00:00Z',
        },
        {
          eventType: 'PROVISIONING_FAILED' as const,
          actorName: 'System',
          details: 'The vSphere infrastructure is temporarily unavailable.',
          occurredAt: '2024-01-01T12:05:00Z',
        },
      ],
    }

    it('displays provisioning failed alert card when status is FAILED', () => {
      mockUseRequestDetail.mockReturnValue({
        data: failedRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      const alertCard = screen.getByTestId('provisioning-failed-alert')
      expect(alertCard).toBeInTheDocument()
      // Title should be within the alert card
      expect(alertCard).toHaveTextContent('Provisioning Failed')
      expect(alertCard).toHaveTextContent('Our team has been notified')
    })

    it('displays error message from timeline event', () => {
      mockUseRequestDetail.mockReturnValue({
        data: failedRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      expect(screen.getByTestId('provisioning-error-message')).toHaveTextContent(
        'Error: The vSphere infrastructure is temporarily unavailable.'
      )
    })

    it('does not display error message section when timeline has no failure details', () => {
      const failedWithoutDetails = {
        ...failedRequestData,
        timeline: [
          ...failedRequestData.timeline.slice(0, 3),
          {
            eventType: 'PROVISIONING_FAILED' as const,
            actorName: 'System',
            details: null,
            occurredAt: '2024-01-01T12:05:00Z',
          },
        ],
      }

      mockUseRequestDetail.mockReturnValue({
        data: failedWithoutDetails,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      expect(screen.getByTestId('provisioning-failed-alert')).toBeInTheDocument()
      expect(screen.queryByTestId('provisioning-error-message')).not.toBeInTheDocument()
    })

    it('does not show cancel button for FAILED status', () => {
      mockUseRequestDetail.mockReturnValue({
        data: failedRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      expect(screen.queryByTestId('cancel-request-button')).not.toBeInTheDocument()
    })

    it('shows Failed status badge', () => {
      mockUseRequestDetail.mockReturnValue({
        data: failedRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<RequestDetail />)

      expect(screen.getByTestId('status-badge-failed')).toBeInTheDocument()
    })
  })
})
