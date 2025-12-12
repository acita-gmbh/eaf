import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useAdminRequestDetail, adminRequestDetailQueryKey } from './useAdminRequestDetail'
import { ApiError } from '@/api/vm-requests'
import type { AdminRequestDetail } from '@/api/admin'

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
    getAdminRequestDetail: vi.fn(),
  }
})

import { useAuth } from 'react-oidc-context'
import { getAdminRequestDetail } from '@/api/admin'

const mockUseAuth = vi.mocked(useAuth)
const mockGetAdminRequestDetail = vi.mocked(getAdminRequestDetail)

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

const mockAdminRequestDetail: AdminRequestDetail = {
  id: 'req-123',
  vmName: 'web-server-01',
  size: 'M',
  cpuCores: 4,
  memoryGb: 8,
  diskGb: 100,
  justification: 'Production web server for customer portal',
  status: 'PENDING',
  projectName: 'Alpha Project',
  requester: {
    id: 'user-456',
    name: 'John Doe',
    email: 'john.doe@example.com',
    role: 'developer',
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
      id: 'req-100',
      vmName: 'dev-server-01',
      status: 'APPROVED',
      createdAt: '2023-12-15T09:00:00Z',
    },
    {
      id: 'req-99',
      vmName: 'test-server-02',
      status: 'CANCELLED',
      createdAt: '2023-12-01T14:30:00Z',
    },
  ],
  createdAt: '2024-01-01T10:00:00Z',
}

describe('useAdminRequestDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
    } as ReturnType<typeof useAuth>)
  })

  it('fetches admin request detail successfully', async () => {
    mockGetAdminRequestDetail.mockResolvedValue(mockAdminRequestDetail)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetAdminRequestDetail).toHaveBeenCalledWith('req-123', 'test-token')
    expect(result.current.data).toEqual(mockAdminRequestDetail)
  })

  it('returns loading state initially', async () => {
    let resolvePromise: (value: AdminRequestDetail) => void
    const pendingPromise = new Promise<AdminRequestDetail>((resolve) => {
      resolvePromise = resolve
    })
    mockGetAdminRequestDetail.mockReturnValue(pendingPromise)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    expect(result.current.isLoading).toBe(true)
    expect(result.current.data).toBeUndefined()

    // Resolve to clean up
    resolvePromise!(mockAdminRequestDetail)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('handles error state', async () => {
    const apiError = new ApiError(500, 'Internal Server Error', {
      message: 'Something went wrong',
    })
    mockGetAdminRequestDetail.mockRejectedValue(apiError)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBe(apiError)
  })

  it('handles 404 Not Found error', async () => {
    const apiError = new ApiError(404, 'Not Found', {
      message: 'Request not found',
    })
    mockGetAdminRequestDetail.mockRejectedValue(apiError)

    const { result } = renderHook(() => useAdminRequestDetail('non-existent'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error?.status).toBe(404)
  })

  it('does not fetch when not authenticated', async () => {
    mockUseAuth.mockReturnValue({
      user: null,
    } as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    // Query should be disabled and not fetching
    expect(result.current.isFetching).toBe(false)
    expect(mockGetAdminRequestDetail).not.toHaveBeenCalled()
  })

  it('does not fetch when access_token is undefined', async () => {
    mockUseAuth.mockReturnValue({
      user: { access_token: undefined },
    } as unknown as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    // Query should be disabled
    expect(result.current.isFetching).toBe(false)
    expect(mockGetAdminRequestDetail).not.toHaveBeenCalled()
  })

  it('does not fetch when requestId is undefined', async () => {
    const { result } = renderHook(() => useAdminRequestDetail(undefined), {
      wrapper: createWrapper(),
    })

    // Query should be disabled
    expect(result.current.isFetching).toBe(false)
    expect(mockGetAdminRequestDetail).not.toHaveBeenCalled()
  })

  it('handles 401 Unauthorized error', async () => {
    const apiError = new ApiError(401, 'Unauthorized', {
      message: 'Token expired',
    })
    mockGetAdminRequestDetail.mockRejectedValue(apiError)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error?.status).toBe(401)
  })

  it('returns requester information correctly (AC 2)', async () => {
    mockGetAdminRequestDetail.mockResolvedValue(mockAdminRequestDetail)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data?.requester).toEqual({
      id: 'user-456',
      name: 'John Doe',
      email: 'john.doe@example.com',
      role: 'developer',
    })
  })

  it('returns request details correctly (AC 3)', async () => {
    mockGetAdminRequestDetail.mockResolvedValue(mockAdminRequestDetail)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    const data = result.current.data
    expect(data?.vmName).toBe('web-server-01')
    expect(data?.size).toBe('M')
    expect(data?.cpuCores).toBe(4)
    expect(data?.memoryGb).toBe(8)
    expect(data?.diskGb).toBe(100)
    expect(data?.justification).toBe('Production web server for customer portal')
    expect(data?.projectName).toBe('Alpha Project')
  })

  it('returns timeline events correctly (AC 5)', async () => {
    mockGetAdminRequestDetail.mockResolvedValue(mockAdminRequestDetail)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data?.timeline).toHaveLength(1)
    expect(result.current.data?.timeline[0]).toEqual({
      eventType: 'CREATED' as const,
      actorName: 'John Doe',
      details: null,
      occurredAt: '2024-01-01T10:00:00Z',
    })
  })

  it('returns requester history correctly (AC 6)', async () => {
    mockGetAdminRequestDetail.mockResolvedValue(mockAdminRequestDetail)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data?.requesterHistory).toHaveLength(2)
    expect(result.current.data?.requesterHistory[0]).toEqual({
      id: 'req-100',
      vmName: 'dev-server-01',
      status: 'APPROVED',
      createdAt: '2023-12-15T09:00:00Z',
    })
  })

  it('uses correct query key for cache', async () => {
    mockGetAdminRequestDetail.mockResolvedValue(mockAdminRequestDetail)

    const { result } = renderHook(() => useAdminRequestDetail('req-123'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    // Query should use the admin-request-detail namespace
    expect(adminRequestDetailQueryKey.detail('req-123')).toEqual([
      'admin-request-detail',
      'req-123',
    ])
  })

  it('re-fetches when requestId changes', async () => {
    mockGetAdminRequestDetail.mockResolvedValue(mockAdminRequestDetail)

    const { result, rerender } = renderHook(
      ({ requestId }: { requestId: string }) => useAdminRequestDetail(requestId),
      {
        wrapper: createWrapper(),
        initialProps: { requestId: 'req-123' },
      }
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    // Change request ID - should trigger new fetch
    const differentDetail = { ...mockAdminRequestDetail, id: 'req-456', vmName: 'other-vm' }
    mockGetAdminRequestDetail.mockResolvedValue(differentDetail)

    rerender({ requestId: 'req-456' })

    await waitFor(() => {
      expect(mockGetAdminRequestDetail).toHaveBeenCalledWith('req-456', 'test-token')
    })
  })

  describe('polling options', () => {
    it('accepts polling configuration without error', async () => {
      mockGetAdminRequestDetail.mockResolvedValue(mockAdminRequestDetail)

      // Test that the hook accepts polling options without throwing
      const { result } = renderHook(
        () => useAdminRequestDetail('req-123', { polling: true, pollInterval: 30000 }),
        { wrapper: createWrapper() }
      )

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      // Verify initial fetch worked
      expect(mockGetAdminRequestDetail).toHaveBeenCalledWith('req-123', 'test-token')
      expect(result.current.data).toEqual(mockAdminRequestDetail)
    })

    it('accepts custom poll interval', async () => {
      mockGetAdminRequestDetail.mockResolvedValue(mockAdminRequestDetail)

      // Test that custom poll interval is accepted
      const { result } = renderHook(
        () => useAdminRequestDetail('req-123', { polling: true, pollInterval: 10000 }),
        { wrapper: createWrapper() }
      )

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(result.current.data).toBeDefined()
    })
  })
})

describe('adminRequestDetailQueryKey', () => {
  it('returns correct base key', () => {
    expect(adminRequestDetailQueryKey.all).toEqual(['admin-request-detail'])
  })

  it('returns correct detail key', () => {
    expect(adminRequestDetailQueryKey.detail('test-id')).toEqual([
      'admin-request-detail',
      'test-id',
    ])
  })
})
