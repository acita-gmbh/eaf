import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useProjects } from './useProjects'
import { ApiError } from '@/api/vm-requests'
import type { Project } from '@/api/admin'

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
    getProjects: vi.fn(),
  }
})

import { useAuth } from 'react-oidc-context'
import { getProjects } from '@/api/admin'

const mockUseAuth = vi.mocked(useAuth)
const mockGetProjects = vi.mocked(getProjects)

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

const mockProjects: Project[] = [
  { id: 'proj-1', name: 'Alpha Project' },
  { id: 'proj-2', name: 'Beta Project' },
  { id: 'proj-3', name: 'Gamma Project' },
]

describe('useProjects', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
    } as ReturnType<typeof useAuth>)
  })

  it('fetches projects successfully', async () => {
    mockGetProjects.mockResolvedValue(mockProjects)

    const { result } = renderHook(() => useProjects(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetProjects).toHaveBeenCalledWith('test-token')
    expect(result.current.data).toEqual(mockProjects)
    expect(result.current.data?.length).toBe(3)
  })

  it('returns loading state initially', async () => {
    let resolvePromise: (value: Project[]) => void
    const pendingPromise = new Promise<Project[]>((resolve) => {
      resolvePromise = resolve
    })
    mockGetProjects.mockReturnValue(pendingPromise)

    const { result } = renderHook(() => useProjects(), {
      wrapper: createWrapper(),
    })

    expect(result.current.isLoading).toBe(true)
    expect(result.current.data).toBeUndefined()

    // Resolve to clean up
    resolvePromise!(mockProjects)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('handles error state', async () => {
    const apiError = new ApiError(500, 'Internal Server Error', {
      message: 'Something went wrong',
    })
    mockGetProjects.mockRejectedValue(apiError)

    const { result } = renderHook(() => useProjects(), {
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

    const { result } = renderHook(() => useProjects(), {
      wrapper: createWrapper(),
    })

    // Query should be disabled and not fetching
    expect(result.current.isFetching).toBe(false)
    expect(mockGetProjects).not.toHaveBeenCalled()
  })

  it('does not fetch when access_token is undefined', async () => {
    mockUseAuth.mockReturnValue({
      user: { access_token: undefined },
    } as unknown as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useProjects(), {
      wrapper: createWrapper(),
    })

    // Query should be disabled
    expect(result.current.isFetching).toBe(false)
    expect(mockGetProjects).not.toHaveBeenCalled()
  })

  it('handles 401 Unauthorized error', async () => {
    const apiError = new ApiError(401, 'Unauthorized', {
      message: 'Token expired',
    })
    mockGetProjects.mockRejectedValue(apiError)

    const { result } = renderHook(() => useProjects(), {
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
    mockGetProjects.mockRejectedValue(apiError)

    const { result } = renderHook(() => useProjects(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error?.status).toBe(403)
  })

  it('returns empty array when no projects exist', async () => {
    mockGetProjects.mockResolvedValue([])

    const { result } = renderHook(() => useProjects(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data).toEqual([])
    expect(result.current.data?.length).toBe(0)
  })

  it('uses correct query key for caching', async () => {
    mockGetProjects.mockResolvedValue(mockProjects)

    // Use single wrapper for both renders to share QueryClient cache
    const Wrapper = createWrapper()

    const { result } = renderHook(() => useProjects(), {
      wrapper: Wrapper,
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    // Second render with same wrapper should use cached data
    mockGetProjects.mockClear()

    const { result: result2 } = renderHook(() => useProjects(), {
      wrapper: Wrapper,
    })

    // With same wrapper, data should be available immediately from cache
    await waitFor(() => expect(result2.current.data).toEqual(mockProjects))
    // API should not have been called again due to caching
    expect(mockGetProjects).not.toHaveBeenCalled()
  })
})
