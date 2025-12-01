import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useMyRequests } from './useMyRequests'
import { ApiError, type PagedVmRequestsResponse } from '@/api/vm-requests'

// Mock react-oidc-context
vi.mock('react-oidc-context', () => ({
  useAuth: vi.fn(() => ({
    user: { access_token: 'test-token' },
  })),
}))

// Mock vm-requests API
vi.mock('@/api/vm-requests', async () => {
  const actual = await vi.importActual<typeof import('@/api/vm-requests')>(
    '@/api/vm-requests'
  )
  return {
    ...actual,
    getMyRequests: vi.fn(),
  }
})

import { useAuth } from 'react-oidc-context'
import { getMyRequests } from '@/api/vm-requests'

const mockUseAuth = vi.mocked(useAuth)
const mockGetMyRequests = vi.mocked(getMyRequests)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children)
  }
}

const mockPagedResponse: PagedVmRequestsResponse = {
  items: [
    {
      id: 'req-1',
      requesterName: 'John Doe',
      projectId: 'proj-1',
      projectName: 'Test Project',
      vmName: 'test-vm',
      size: 'M',
      cpuCores: 4,
      memoryGb: 16,
      diskGb: 100,
      justification: 'Test reason',
      status: 'PENDING',
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
}

describe('useMyRequests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
    } as ReturnType<typeof useAuth>)
  })

  it('fetches my requests with default pagination', async () => {
    mockGetMyRequests.mockResolvedValue(mockPagedResponse)

    const { result } = renderHook(() => useMyRequests(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetMyRequests).toHaveBeenCalledWith({}, 'test-token')
    expect(result.current.data).toEqual(mockPagedResponse)
  })

  it('fetches my requests with custom pagination parameters', async () => {
    mockGetMyRequests.mockResolvedValue({
      ...mockPagedResponse,
      page: 2,
      size: 25,
    })

    const { result } = renderHook(() => useMyRequests({ page: 2, size: 25 }), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetMyRequests).toHaveBeenCalledWith({ page: 2, size: 25 }, 'test-token')
    expect(result.current.data?.page).toBe(2)
    expect(result.current.data?.size).toBe(25)
  })

  it('returns loading state initially', async () => {
    let resolvePromise: (value: PagedVmRequestsResponse) => void
    const pendingPromise = new Promise<PagedVmRequestsResponse>((resolve) => {
      resolvePromise = resolve
    })
    mockGetMyRequests.mockReturnValue(pendingPromise)

    const { result } = renderHook(() => useMyRequests(), {
      wrapper: createWrapper(),
    })

    expect(result.current.isLoading).toBe(true)
    expect(result.current.data).toBeUndefined()

    // Resolve to clean up
    resolvePromise!(mockPagedResponse)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('handles error state', async () => {
    const apiError = new ApiError(500, 'Internal Server Error', {
      message: 'Something went wrong',
    })
    mockGetMyRequests.mockRejectedValue(apiError)

    const { result } = renderHook(() => useMyRequests(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBe(apiError)
  })

  it('does not fetch when not authenticated', async () => {
    mockUseAuth.mockReturnValue({
      user: null,
    } as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useMyRequests(), {
      wrapper: createWrapper(),
    })

    // Query should be disabled and not fetching
    expect(result.current.isFetching).toBe(false)
    expect(mockGetMyRequests).not.toHaveBeenCalled()
  })

  it('does not fetch when access_token is undefined', async () => {
    mockUseAuth.mockReturnValue({
      user: { access_token: undefined },
    } as unknown as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useMyRequests(), {
      wrapper: createWrapper(),
    })

    // Query should be disabled
    expect(result.current.isFetching).toBe(false)
    expect(mockGetMyRequests).not.toHaveBeenCalled()
  })

  it('handles 401 Unauthorized error', async () => {
    const apiError = new ApiError(401, 'Unauthorized', {
      message: 'Token expired',
    })
    mockGetMyRequests.mockRejectedValue(apiError)

    const { result } = renderHook(() => useMyRequests(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error?.status).toBe(401)
  })

  it('returns pagination metadata correctly', async () => {
    const pagedResponse: PagedVmRequestsResponse = {
      items: [],
      page: 1,
      size: 10,
      totalElements: 50,
      totalPages: 5,
      hasNext: true,
      hasPrevious: true,
    }
    mockGetMyRequests.mockResolvedValue(pagedResponse)

    const { result } = renderHook(() => useMyRequests({ page: 1, size: 10 }), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data?.totalElements).toBe(50)
    expect(result.current.data?.totalPages).toBe(5)
    expect(result.current.data?.hasNext).toBe(true)
    expect(result.current.data?.hasPrevious).toBe(true)
  })
})
