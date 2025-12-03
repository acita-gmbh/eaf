import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@/test/test-utils'
import userEvent from '@testing-library/user-event'
import { ProjectFilter } from './ProjectFilter'

// Mock useProjects hook
const mockUseProjects = vi.hoisted(() =>
  vi.fn(() => ({
    data: null,
    isLoading: true,
    isError: false,
    refetch: vi.fn(),
  }))
)

vi.mock('@/hooks/useProjects', () => ({
  useProjects: mockUseProjects,
}))

describe('ProjectFilter', () => {
  const mockOnChange = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Loading State', () => {
    it('shows loading skeleton while projects are being fetched', () => {
      mockUseProjects.mockReturnValue({
        data: null,
        isLoading: true,
        isError: false,
        refetch: vi.fn(),
      })

      render(<ProjectFilter value={undefined} onChange={mockOnChange} />)

      expect(screen.getByTestId('project-filter-loading')).toBeInTheDocument()
      expect(screen.getByText('Project:')).toBeInTheDocument()
    })
  })

  describe('Error State', () => {
    it('shows error message when projects fail to load', () => {
      mockUseProjects.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        refetch: vi.fn(),
      })

      render(<ProjectFilter value={undefined} onChange={mockOnChange} />)

      expect(screen.getByTestId('project-filter-error')).toBeInTheDocument()
      expect(screen.getByText('Failed to load projects')).toBeInTheDocument()
    })

    it('calls refetch when retry button is clicked', async () => {
      const refetchMock = vi.fn()
      mockUseProjects.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        refetch: refetchMock,
      })

      const user = userEvent.setup()
      render(<ProjectFilter value={undefined} onChange={mockOnChange} />)

      await user.click(screen.getByRole('button', { name: /retry loading projects/i }))

      expect(refetchMock).toHaveBeenCalled()
    })
  })

  describe('Success State', () => {
    beforeEach(() => {
      mockUseProjects.mockReturnValue({
        data: [
          { id: 'proj-1', name: 'Project Alpha' },
          { id: 'proj-2', name: 'Project Beta' },
        ],
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      })
    })

    it('renders project filter dropdown', () => {
      render(<ProjectFilter value={undefined} onChange={mockOnChange} />)

      expect(screen.getByTestId('project-filter')).toBeInTheDocument()
      expect(screen.getByTestId('project-filter-trigger')).toBeInTheDocument()
      expect(screen.getByText('Project:')).toBeInTheDocument()
    })

    it('shows "All Projects" as default when value is undefined', () => {
      render(<ProjectFilter value={undefined} onChange={mockOnChange} />)

      expect(screen.getByText('All Projects')).toBeInTheDocument()
    })

    // Note: Radix Select interaction (opening dropdown, selecting options)
    // doesn't work reliably in JSDOM. Full interaction is tested in E2E tests.
  })
})
