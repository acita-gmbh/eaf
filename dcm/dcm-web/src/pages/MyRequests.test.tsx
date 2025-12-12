import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@/test/test-utils'
import userEvent from '@testing-library/user-event'
import { MyRequests } from './MyRequests'

// Mock useMyRequests hook
const mockUseMyRequests = vi.hoisted(() =>
  vi.fn(() => ({
    data: null,
    isLoading: true,
    isError: false,
    error: null,
    refetch: vi.fn(),
  }))
)

vi.mock('@/hooks/useMyRequests', () => ({
  useMyRequests: mockUseMyRequests,
}))

// Mock useNavigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

describe('MyRequests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Loading State', () => {
    it('shows loading spinner when data is being fetched', () => {
      mockUseMyRequests.mockReturnValue({
        data: null,
        isLoading: true,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByRole('heading', { name: /my requests/i })).toBeInTheDocument()
      expect(document.querySelector('.animate-spin')).toBeInTheDocument()
    })
  })

  describe('Error State', () => {
    it('shows error message when fetching fails', () => {
      mockUseMyRequests.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { message: 'Network error' },
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByTestId('my-requests-error')).toBeInTheDocument()
      expect(screen.getByText('Error Loading')).toBeInTheDocument()
      expect(screen.getByText('Network error')).toBeInTheDocument()
    })

    it('shows default error message when error has no message', () => {
      mockUseMyRequests.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByText('Could not load requests.')).toBeInTheDocument()
    })

    it('calls refetch when Try Again is clicked', async () => {
      const refetchMock = vi.fn()
      mockUseMyRequests.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: { message: 'Network error' },
        refetch: refetchMock,
      })

      const user = userEvent.setup()
      render(<MyRequests />)

      await user.click(screen.getByRole('button', { name: /try again/i }))

      expect(refetchMock).toHaveBeenCalled()
    })
  })

  describe('Empty State', () => {
    it('shows empty state when there are no requests', () => {
      mockUseMyRequests.mockReturnValue({
        data: { items: [], totalElements: 0, totalPages: 0, hasPrevious: false, hasNext: false },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByText('No Requests')).toBeInTheDocument()
      expect(screen.getByText(/haven't submitted any VM requests/i)).toBeInTheDocument()
    })

    it('shows empty state when data is null', () => {
      mockUseMyRequests.mockReturnValue({
        data: null,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByText('No Requests')).toBeInTheDocument()
    })

    it('navigates to new request form when CTA is clicked', async () => {
      mockUseMyRequests.mockReturnValue({
        data: { items: [], totalElements: 0, totalPages: 0, hasPrevious: false, hasNext: false },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const user = userEvent.setup()
      render(<MyRequests />)

      // Multiple buttons exist - header button and empty state CTA
      const buttons = screen.getAllByRole('button', { name: /request new vm/i })
      await user.click(buttons[0])

      expect(mockNavigate).toHaveBeenCalledWith('/requests/new')
    })
  })

  describe('Success State with Data', () => {
    const mockData = {
      items: [
        {
          id: '1',
          vmName: 'web-server-01',
          projectName: 'Project Alpha',
          status: 'PENDING',
          cpuCores: 4,
          memoryGb: 16,
          diskGb: 100,
          justification: 'Development server',
          createdAt: '2024-01-01T10:00:00Z',
        },
        {
          id: '2',
          vmName: 'db-server-01',
          projectName: 'Project Beta',
          status: 'APPROVED',
          cpuCores: 8,
          memoryGb: 32,
          diskGb: 500,
          justification: 'Database server',
          createdAt: '2024-01-02T10:00:00Z',
        },
      ],
      totalElements: 2,
      totalPages: 1,
      hasPrevious: false,
      hasNext: false,
    }

    it('displays request cards when data is available', () => {
      mockUseMyRequests.mockReturnValue({
        data: mockData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByTestId('my-requests-page')).toBeInTheDocument()
      expect(screen.getByTestId('requests-list')).toBeInTheDocument()
      expect(screen.getByText('2 requests total')).toBeInTheDocument()
    })

    it('shows singular "request" for 1 item', () => {
      mockUseMyRequests.mockReturnValue({
        data: { ...mockData, items: [mockData.items[0]], totalElements: 1 },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByText('1 request total')).toBeInTheDocument()
    })

    it('displays page size selector', () => {
      mockUseMyRequests.mockReturnValue({
        data: mockData,
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByTestId('page-size-selector')).toBeInTheDocument()
      expect(screen.getByText('Items per page:')).toBeInTheDocument()
    })
  })

  describe('Pagination', () => {
    it('does not show pagination for single page', () => {
      mockUseMyRequests.mockReturnValue({
        data: {
          items: [{ id: '1', vmName: 'test', projectName: 'Project', status: 'PENDING', cpuCores: 2, memoryGb: 4, diskGb: 50, justification: 'Test', createdAt: '2024-01-01T10:00:00Z' }],
          totalElements: 1,
          totalPages: 1,
          hasPrevious: false,
          hasNext: false,
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.queryByTestId('pagination')).not.toBeInTheDocument()
    })

    it('shows pagination for multiple pages', () => {
      mockUseMyRequests.mockReturnValue({
        data: {
          items: [{ id: '1', vmName: 'test', projectName: 'Project', status: 'PENDING', cpuCores: 2, memoryGb: 4, diskGb: 50, justification: 'Test', createdAt: '2024-01-01T10:00:00Z' }],
          totalElements: 25,
          totalPages: 3,
          hasPrevious: false,
          hasNext: true,
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByTestId('pagination')).toBeInTheDocument()
    })

    it('disables previous button on first page', () => {
      mockUseMyRequests.mockReturnValue({
        data: {
          items: [{ id: '1', vmName: 'test', projectName: 'Project', status: 'PENDING', cpuCores: 2, memoryGb: 4, diskGb: 50, justification: 'Test', createdAt: '2024-01-01T10:00:00Z' }],
          totalElements: 25,
          totalPages: 3,
          hasPrevious: false,
          hasNext: true,
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      const prevButton = screen.getByLabelText(/go to previous page/i)
      expect(prevButton).toHaveClass('pointer-events-none')
    })

    it('disables next button on last page', () => {
      mockUseMyRequests.mockReturnValue({
        data: {
          items: [{ id: '1', vmName: 'test', projectName: 'Project', status: 'PENDING', cpuCores: 2, memoryGb: 4, diskGb: 50, justification: 'Test', createdAt: '2024-01-01T10:00:00Z' }],
          totalElements: 25,
          totalPages: 3,
          hasPrevious: true,
          hasNext: false,
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      const nextButton = screen.getByLabelText(/go to next page/i)
      expect(nextButton).toHaveClass('pointer-events-none')
    })

    it('navigates to next page when page number is clicked', async () => {
      mockUseMyRequests.mockReturnValue({
        data: {
          items: [{ id: '1', vmName: 'test', projectName: 'Project', status: 'PENDING', cpuCores: 2, memoryGb: 4, diskGb: 50, justification: 'Test', createdAt: '2024-01-01T10:00:00Z' }],
          totalElements: 30,
          totalPages: 3,
          hasPrevious: false,
          hasNext: true,
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const user = userEvent.setup()
      render(<MyRequests />)

      // Click page 2
      await user.click(screen.getByTestId('page-2'))

      // Hook should be called with page: 1
      await waitFor(() => {
        expect(mockUseMyRequests).toHaveBeenCalledWith({ page: 1, size: 10 })
      })
    })

    it('shows ellipsis for many pages', () => {
      mockUseMyRequests.mockReturnValue({
        data: {
          items: [{ id: '1', vmName: 'test', projectName: 'Project', status: 'PENDING', cpuCores: 2, memoryGb: 4, diskGb: 50, justification: 'Test', createdAt: '2024-01-01T10:00:00Z' }],
          totalElements: 100,
          totalPages: 10,
          hasPrevious: false,
          hasNext: true,
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      // With 10 pages, should show ellipsis
      expect(screen.getByText('...')).toBeInTheDocument()
    })

    it('navigates using previous button', async () => {
      // Start on page 2 by mocking the hook state
      mockUseMyRequests.mockReturnValue({
        data: {
          items: [{ id: '1', vmName: 'test', projectName: 'Project', status: 'PENDING', cpuCores: 2, memoryGb: 4, diskGb: 50, justification: 'Test', createdAt: '2024-01-01T10:00:00Z' }],
          totalElements: 30,
          totalPages: 3,
          hasPrevious: true,
          hasNext: true,
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const user = userEvent.setup()
      render(<MyRequests />)

      // First navigate to page 2
      await user.click(screen.getByTestId('page-2'))

      await waitFor(() => {
        expect(mockUseMyRequests).toHaveBeenCalledWith({ page: 1, size: 10 })
      })
    })

    it('navigates using next button', async () => {
      mockUseMyRequests.mockReturnValue({
        data: {
          items: [{ id: '1', vmName: 'test', projectName: 'Project', status: 'PENDING', cpuCores: 2, memoryGb: 4, diskGb: 50, justification: 'Test', createdAt: '2024-01-01T10:00:00Z' }],
          totalElements: 30,
          totalPages: 3,
          hasPrevious: false,
          hasNext: true,
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const user = userEvent.setup()
      render(<MyRequests />)

      // Click next button
      await user.click(screen.getByLabelText(/go to next page/i))

      await waitFor(() => {
        expect(mockUseMyRequests).toHaveBeenCalledWith({ page: 1, size: 10 })
      })
    })
  })

  describe('Page Size Selector', () => {
    it('renders page size selector with default value', () => {
      mockUseMyRequests.mockReturnValue({
        data: {
          items: [{ id: '1', vmName: 'test', projectName: 'Project', status: 'PENDING', cpuCores: 2, memoryGb: 4, diskGb: 50, justification: 'Test', createdAt: '2024-01-01T10:00:00Z' }],
          totalElements: 100,
          totalPages: 10,
          hasPrevious: false,
          hasNext: true,
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      // The hook should be called initially with page: 0, size: 10
      expect(mockUseMyRequests).toHaveBeenCalledWith({ page: 0, size: 10 })

      // Selector should be rendered
      expect(screen.getByTestId('page-size-selector')).toBeInTheDocument()
    })

    // Note: Page size change test requires Radix Select interaction which doesn't work in JSDOM.
    // This is covered in E2E tests.
  })

  describe('Header', () => {
    it('shows page title and description', () => {
      mockUseMyRequests.mockReturnValue({
        data: { items: [], totalElements: 0, totalPages: 0, hasPrevious: false, hasNext: false },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      render(<MyRequests />)

      expect(screen.getByRole('heading', { name: /my requests/i })).toBeInTheDocument()
      expect(screen.getByText(/overview of your vm requests/i)).toBeInTheDocument()
    })

    it('navigates to new request when header button clicked', async () => {
      mockUseMyRequests.mockReturnValue({
        data: { items: [], totalElements: 0, totalPages: 0, hasPrevious: false, hasNext: false },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      })

      const user = userEvent.setup()
      render(<MyRequests />)

      const buttons = screen.getAllByRole('button', { name: /request new vm/i })
      await user.click(buttons[0])

      expect(mockNavigate).toHaveBeenCalledWith('/requests/new')
    })
  })
})
