import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { usePendingRequests } from './usePendingRequests'
import { ApiError } from '@/api/vm-requests'
import type { PendingRequestsPage } from '@/api/admin'

// Mock react-oidc-context
vi.mock('react-oidc-context', () => ({
  useAuth: vi.fn(() => ({
    user: { access_token: 'test-token' },
  })),
}))

// Mock admin API
vi.mock('@/api/admin', async () => {
  const actual = await vi.importActual<typeof import('@/api/admin')>('@/api/admin')
  return {
    ...actual,
    getPendingRequests: vi.fn(),
  }
})

import { useAuth } from 'react-oidc-context'
import { getPendingRequests } from '@/api/admin'

const mockUseAuth = vi.mocked(useAuth)
const mockGetPendingRequests = vi.mocked(getPendingRequests)

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

const mockPagedResponse: PendingRequestsPage = {
  items: [
    {
      id: 'req-1',
      requesterName: 'John Doe',
      vmName: 'test-vm',
      projectName: 'Test Project',
      size: 'M',
      cpuCores: 4,
      memoryGb: 8,
      diskGb: 100,
      createdAt: '2024-01-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 25,
  totalElements: 1,
  totalPages: 1,
}

describe('usePendingRequests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
    } as ReturnType<typeof useAuth>)
  })

  it('fetches pending requests with default pagination', async () => {
    mockGetPendingRequests.mockResolvedValue(mockPagedResponse)

    const { result } = renderHook(() => usePendingRequests(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetPendingRequests).toHaveBeenCalledWith(
      { page: 0, size: 25, projectId: undefined },
      'test-token'
    )
    expect(result.current.data).toEqual(mockPagedResponse)
  })

  it('fetches pending requests with custom pagination parameters', async () => {
    mockGetPendingRequests.mockResolvedValue({
      ...mockPagedResponse,
      page: 2,
      size: 50,
    })

    const { result } = renderHook(() => usePendingRequests({ page: 2, size: 50 }), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetPendingRequests).toHaveBeenCalledWith(
      { page: 2, size: 50, projectId: undefined },
      'test-token'
    )
    expect(result.current.data?.page).toBe(2)
    expect(result.current.data?.size).toBe(50)
  })

  it('fetches pending requests with project filter', async () => {
    const projectId = 'proj-123'
    mockGetPendingRequests.mockResolvedValue(mockPagedResponse)

    const { result } = renderHook(() => usePendingRequests({ projectId }), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetPendingRequests).toHaveBeenCalledWith(
      { page: 0, size: 25, projectId: 'proj-123' },
      'test-token'
    )
  })

  it('returns loading state initially', async () => {
    let resolvePromise: (value: PendingRequestsPage) => void
    const pendingPromise = new Promise<PendingRequestsPage>((resolve) => {
      resolvePromise = resolve
    })
    mockGetPendingRequests.mockReturnValue(pendingPromise)

    const { result } = renderHook(() => usePendingRequests(), {
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
    mockGetPendingRequests.mockRejectedValue(apiError)

    const { result } = renderHook(() => usePendingRequests(), {
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

    const { result } = renderHook(() => usePendingRequests(), {
      wrapper: createWrapper(),
    })

    // Query should be disabled and not fetching
    expect(result.current.isFetching).toBe(false)
    expect(mockGetPendingRequests).not.toHaveBeenCalled()
  })

  it('does not fetch when access_token is undefined', async () => {
    mockUseAuth.mockReturnValue({
      user: { access_token: undefined },
    } as unknown as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => usePendingRequests(), {
      wrapper: createWrapper(),
    })

    // Query should be disabled
    expect(result.current.isFetching).toBe(false)
    expect(mockGetPendingRequests).not.toHaveBeenCalled()
  })

  it('handles 401 Unauthorized error', async () => {
    const apiError = new ApiError(401, 'Unauthorized', {
      message: 'Token expired',
    })
    mockGetPendingRequests.mockRejectedValue(apiError)

    const { result } = renderHook(() => usePendingRequests(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error?.status).toBe(401)
  })

  it('handles 403 Forbidden error for non-admin users', async () => {
    const apiError = new ApiError(403, 'Forbidden', {
      message: 'Admin role required',
    })
    mockGetPendingRequests.mockRejectedValue(apiError)

    const { result } = renderHook(() => usePendingRequests(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error?.status).toBe(403)
  })

  it('returns pagination metadata correctly', async () => {
    const pagedResponse: PendingRequestsPage = {
      items: [],
      page: 1,
      size: 10,
      totalElements: 50,
      totalPages: 5,
    }
    mockGetPendingRequests.mockResolvedValue(pagedResponse)

    const { result } = renderHook(() => usePendingRequests({ page: 1, size: 10 }), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data?.totalElements).toBe(50)
    expect(result.current.data?.totalPages).toBe(5)
  })

  it('includes all query params in cache key for proper invalidation', async () => {
    mockGetPendingRequests.mockResolvedValue(mockPagedResponse)

    // First render with one set of params
    const { result: result1, rerender: rerender1 } = renderHook(
      (props: { projectId?: string; page?: number; size?: number } = {}) =>
        usePendingRequests(props),
      {
        wrapper: createWrapper(),
        initialProps: { page: 0 },
      }
    )

    await waitFor(() => expect(result1.current.isSuccess).toBe(true))

    // Change params - should trigger new fetch
    rerender1({ page: 1 })

    await waitFor(() => {
      expect(mockGetPendingRequests).toHaveBeenCalledTimes(2)
    })
  })
})
