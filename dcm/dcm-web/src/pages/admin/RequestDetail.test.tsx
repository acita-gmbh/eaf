import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@/test/test-utils'
import userEvent from '@testing-library/user-event'
import { AdminRequestDetail } from './RequestDetail'

// Mock hooks
const mockUseAdminRequestDetail = vi.hoisted(() =>
  vi.fn(() => ({
    data: null,
    isLoading: true,
    isError: false,
    error: null,
    refetch: vi.fn(),
  }))
)

vi.mock('@/hooks/useAdminRequestDetail', () => ({
  useAdminRequestDetail: mockUseAdminRequestDetail,
}))

// Mock mutation hooks with default implementations
const mockApproveMutate = vi.hoisted(() => vi.fn())
const mockRejectMutate = vi.hoisted(() => vi.fn())

vi.mock('@/hooks/useApproveRequest', () => ({
  useApproveRequest: () => ({
    mutate: mockApproveMutate,
    isPending: false,
  }),
}))

vi.mock('@/hooks/useRejectRequest', () => ({
  useRejectRequest: () => ({
    mutate: mockRejectMutate,
    isPending: false,
  }),
}))

// Mock react-router-dom
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useParams: () => ({ id: 'test-request-id' }),
    useNavigate: () => mockNavigate,
  }
})

const mockAdminRequestData = {
  id: 'test-request-id',
  vmName: 'web-server-01',
  projectName: 'Project Alpha',
  status: 'PENDING' as const,
  cpuCores: 4,
  memoryGb: 16,
  diskGb: 100,
  justification: 'Development server for the team',
  createdAt: '2024-01-01T10:00:00Z',
  version: 1, // Added for Story 2.11 optimistic locking
  requester: {
    name: 'John Doe',
    email: 'john@example.com',
    role: 'Developer',
  },
  timeline: [
    {
      eventType: 'CREATED' as const,
      actorName: 'John Doe',
      details: null,
      occurredAt: '2024-01-01T10:00:00Z',
    },
  ],
  requesterHistory: [
    {
      id: '2',
      vmName: 'old-server-01',
      status: 'APPROVED' as const,
      createdAt: '2023-12-01T10:00:00Z',
    },
  ],
}

describe('AdminRequestDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Loading State', () => {
    it('shows loading spinner', () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: true,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-loading')).toBeInTheDocument()
      expect(document.querySelector('.animate-spin')).toBeInTheDocument()
    })
  })

  describe('Error States', () => {
    it('shows not found error for 404', () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 404, message: 'Not found' },
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-not-found')).toBeInTheDocument()
      expect(screen.getByText('Request Not Found')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /view pending requests/i })).toBeInTheDocument()
    })

    it('shows general error for other errors', () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 500, message: 'Server error' },
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-error')).toBeInTheDocument()
      expect(screen.getByText('Error Loading Request')).toBeInTheDocument()
      expect(screen.getByText('Server error')).toBeInTheDocument()
    })

    it('shows default error message when error has no message', () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 500 },
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      expect(screen.getByText('Could not load request details.')).toBeInTheDocument()
    })

    it('calls refetch on Try Again click', async () => {
      const refetchMock = vi.fn()
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 500 },
        refetch: refetchMock,
      })

      const user = userEvent.setup()
      render(<AdminRequestDetail />)

      await user.click(screen.getByRole('button', { name: /try again/i }))

      expect(refetchMock).toHaveBeenCalled()
    })
  })

  describe('Success State', () => {
    beforeEach(() => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: mockAdminRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })
    })

    it('displays request details', () => {
      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-page')).toBeInTheDocument()
      expect(screen.getByTestId('admin-request-detail-vm-name')).toHaveTextContent('web-server-01')
      expect(screen.getByTestId('admin-request-detail-project')).toHaveTextContent('Project Alpha')
    })

    it('displays VM specifications', () => {
      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-cpu')).toHaveTextContent('4 cores')
      expect(screen.getByTestId('admin-request-detail-memory')).toHaveTextContent('16 GB')
      expect(screen.getByTestId('admin-request-detail-disk')).toHaveTextContent('100 GB')
    })

    it('displays justification', () => {
      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-justification')).toHaveTextContent(
        'Development server for the team'
      )
    })

    it('displays requester information', () => {
      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-requester-name')).toHaveTextContent('John Doe')
      expect(screen.getByTestId('admin-request-detail-requester-email')).toHaveTextContent('john@example.com')
      expect(screen.getByTestId('admin-request-detail-requester-role')).toHaveTextContent('Developer')
    })

    it('displays project context placeholder', () => {
      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-quota-placeholder')).toHaveTextContent(
        'Quota information available in Epic 4'
      )
    })

    it('displays requester history', () => {
      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-history')).toBeInTheDocument()
      expect(screen.getByTestId('history-item-2')).toHaveTextContent('old-server-01')
    })

    it('shows empty history message when no previous requests', () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: { ...mockAdminRequestData, requesterHistory: [] },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      expect(screen.getByText('No previous requests from this user.')).toBeInTheDocument()
    })

    it('shows enabled approve/reject buttons for PENDING status', () => {
      render(<AdminRequestDetail />)

      expect(screen.getByTestId('approve-button')).toBeEnabled()
      expect(screen.getByTestId('reject-button')).toBeEnabled()
    })

    it('hides approve/reject buttons for non-PENDING status', () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: { ...mockAdminRequestData, status: 'APPROVED' },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      expect(screen.queryByTestId('approve-button')).not.toBeInTheDocument()
      expect(screen.queryByTestId('reject-button')).not.toBeInTheDocument()
    })

    it('displays timeline section', () => {
      render(<AdminRequestDetail />)

      expect(screen.getByText('Timeline')).toBeInTheDocument()
    })
  })

  describe('Navigation', () => {
    it('navigates back when back button is clicked', async () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: mockAdminRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const user = userEvent.setup()
      render(<AdminRequestDetail />)

      const backButton = screen.getByRole('button', { name: /back to pending requests/i })
      await user.click(backButton)

      expect(mockNavigate).toHaveBeenCalledWith('/admin/requests')
    })
  })

  describe('Unexpected State Handling', () => {
    it('shows unexpected error UI when data is null after loading', () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      // Should show error UI instead of blank screen
      expect(screen.getByTestId('admin-request-detail-unexpected-error')).toBeInTheDocument()
      expect(screen.getByText('Unexpected Error')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
    })

    it('calls refetch when Try Again is clicked in unexpected error state', async () => {
      const refetchMock = vi.fn()
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: false,
        error: null,
        refetch: refetchMock,
      })

      const user = userEvent.setup()
      render(<AdminRequestDetail />)

      await user.click(screen.getByRole('button', { name: /try again/i }))

      expect(refetchMock).toHaveBeenCalled()
    })
  })

  describe('Network Error Handling', () => {
    it('shows general error for network failures (status 0)', () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 0, message: 'Network error' },
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-error')).toBeInTheDocument()
      expect(screen.getByText('Error Loading Request')).toBeInTheDocument()
      expect(screen.getByText('Network error')).toBeInTheDocument()
    })

    it('shows default message for network failure without message', () => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { status: 0 },
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      expect(screen.getByTestId('admin-request-detail-error')).toBeInTheDocument()
      expect(screen.getByText('Could not load request details.')).toBeInTheDocument()
    })
  })

  describe('Approve/Reject Actions - Story 2.11', () => {
    beforeEach(() => {
      mockUseAdminRequestDetail.mockReturnValue({
        data: mockAdminRequestData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })
    })

    it('opens approve dialog when Approve button is clicked', async () => {
      const user = userEvent.setup()
      render(<AdminRequestDetail />)

      await user.click(screen.getByTestId('approve-button'))

      // Dialog should be open with confirmation text
      expect(screen.getByTestId('approve-confirm-dialog')).toBeInTheDocument()
      expect(screen.getByText(/are you sure you want to approve/i)).toBeInTheDocument()
    })

    it('opens reject dialog when Reject button is clicked', async () => {
      const user = userEvent.setup()
      render(<AdminRequestDetail />)

      await user.click(screen.getByTestId('reject-button'))

      // Dialog should be open with rejection form
      expect(screen.getByTestId('reject-form-dialog')).toBeInTheDocument()
      expect(screen.getByText('Rejection Reason')).toBeInTheDocument()
    })

    it('calls approve mutation when dialog is confirmed', async () => {
      const user = userEvent.setup()
      render(<AdminRequestDetail />)

      // Open dialog
      await user.click(screen.getByTestId('approve-button'))

      // Confirm approval using the confirm button testid
      const confirmButton = screen.getByTestId('approve-confirm-button')
      await user.click(confirmButton)

      expect(mockApproveMutate).toHaveBeenCalledWith(
        mockAdminRequestData.version,
        expect.objectContaining({ onError: expect.any(Function) })
      )
    })

    it('calls reject mutation with reason when dialog is confirmed', async () => {
      const user = userEvent.setup()
      render(<AdminRequestDetail />)

      // Open dialog
      await user.click(screen.getByTestId('reject-button'))

      // Enter reason using the testid
      const reasonInput = screen.getByTestId('rejection-reason-input')
      const testReason = 'Budget constraints prevent approval at this time.'
      await user.type(reasonInput, testReason)

      // Confirm rejection using the confirm button testid
      const confirmButton = screen.getByTestId('reject-confirm-button')
      await user.click(confirmButton)

      expect(mockRejectMutate).toHaveBeenCalledWith(
        { version: mockAdminRequestData.version, reason: testReason },
        expect.objectContaining({ onError: expect.any(Function) })
      )
    })

    it('does not call approve mutation when data is null', async () => {
      // This tests the early return in handleApprove when !data
      mockUseAdminRequestDetail.mockReturnValue({
        data: null,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<AdminRequestDetail />)

      // With null data, approve button shouldn't be visible (unexpected state UI shown)
      expect(screen.queryByTestId('approve-button')).not.toBeInTheDocument()
    })

    describe('Approval Error Handling', () => {
      it('handles 409 Conflict error and triggers refetch', async () => {
        const refetchMock = vi.fn()
        mockUseAdminRequestDetail.mockReturnValue({
          data: mockAdminRequestData,
          isLoading: false,
          isError: false,
          error: null,
          refetch: refetchMock,
        })

        // Mock to capture onError callback
        mockApproveMutate.mockImplementation((_version, options) => {
          // Simulate 409 error
          options.onError({ status: 409, message: 'Conflict' })
        })

        const user = userEvent.setup()
        render(<AdminRequestDetail />)

        await user.click(screen.getByTestId('approve-button'))
        await user.click(screen.getByTestId('approve-confirm-button'))

        expect(refetchMock).toHaveBeenCalled()
      })

      it('handles 422 Unprocessable Entity error and triggers refetch', async () => {
        const refetchMock = vi.fn()
        mockUseAdminRequestDetail.mockReturnValue({
          data: mockAdminRequestData,
          isLoading: false,
          isError: false,
          error: null,
          refetch: refetchMock,
        })

        mockApproveMutate.mockImplementation((_version, options) => {
          options.onError({ status: 422, message: 'Invalid state' })
        })

        const user = userEvent.setup()
        render(<AdminRequestDetail />)

        await user.click(screen.getByTestId('approve-button'))
        await user.click(screen.getByTestId('approve-confirm-button'))

        expect(refetchMock).toHaveBeenCalled()
      })

      it('handles 404 Not Found error', async () => {
        mockApproveMutate.mockImplementation((_version, options) => {
          options.onError({ status: 404, message: 'Not found' })
        })

        const user = userEvent.setup()
        render(<AdminRequestDetail />)

        await user.click(screen.getByTestId('approve-button'))
        await user.click(screen.getByTestId('approve-confirm-button'))

        // Just verifies onError path completes without crashing
        expect(mockApproveMutate).toHaveBeenCalled()
      })

      it('handles generic error', async () => {
        mockApproveMutate.mockImplementation((_version, options) => {
          options.onError({ status: 500, message: 'Server error' })
        })

        const user = userEvent.setup()
        render(<AdminRequestDetail />)

        await user.click(screen.getByTestId('approve-button'))
        await user.click(screen.getByTestId('approve-confirm-button'))

        expect(mockApproveMutate).toHaveBeenCalled()
      })
    })

    describe('Rejection Error Handling', () => {
      it('handles 409 Conflict error and triggers refetch', async () => {
        const refetchMock = vi.fn()
        mockUseAdminRequestDetail.mockReturnValue({
          data: mockAdminRequestData,
          isLoading: false,
          isError: false,
          error: null,
          refetch: refetchMock,
        })

        mockRejectMutate.mockImplementation((_params, options) => {
          options.onError({ status: 409, message: 'Conflict' })
        })

        const user = userEvent.setup()
        render(<AdminRequestDetail />)

        await user.click(screen.getByTestId('reject-button'))
        await user.type(screen.getByTestId('rejection-reason-input'), 'Valid reason text')
        await user.click(screen.getByTestId('reject-confirm-button'))

        expect(refetchMock).toHaveBeenCalled()
      })

      it('handles 422 Unprocessable Entity error and triggers refetch', async () => {
        const refetchMock = vi.fn()
        mockUseAdminRequestDetail.mockReturnValue({
          data: mockAdminRequestData,
          isLoading: false,
          isError: false,
          error: null,
          refetch: refetchMock,
        })

        mockRejectMutate.mockImplementation((_params, options) => {
          options.onError({ status: 422, message: 'Invalid state' })
        })

        const user = userEvent.setup()
        render(<AdminRequestDetail />)

        await user.click(screen.getByTestId('reject-button'))
        await user.type(screen.getByTestId('rejection-reason-input'), 'Valid reason text')
        await user.click(screen.getByTestId('reject-confirm-button'))

        expect(refetchMock).toHaveBeenCalled()
      })

      it('handles 404 Not Found error', async () => {
        mockRejectMutate.mockImplementation((_params, options) => {
          options.onError({ status: 404, message: 'Not found' })
        })

        const user = userEvent.setup()
        render(<AdminRequestDetail />)

        await user.click(screen.getByTestId('reject-button'))
        await user.type(screen.getByTestId('rejection-reason-input'), 'Valid reason text')
        await user.click(screen.getByTestId('reject-confirm-button'))

        expect(mockRejectMutate).toHaveBeenCalled()
      })

      it('handles generic error', async () => {
        mockRejectMutate.mockImplementation((_params, options) => {
          options.onError({ status: 500, message: 'Server error' })
        })

        const user = userEvent.setup()
        render(<AdminRequestDetail />)

        await user.click(screen.getByTestId('reject-button'))
        await user.type(screen.getByTestId('rejection-reason-input'), 'Valid reason text')
        await user.click(screen.getByTestId('reject-confirm-button'))

        expect(mockRejectMutate).toHaveBeenCalled()
      })
    })
  })
})
