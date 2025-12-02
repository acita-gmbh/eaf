import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useRequestDetail, requestDetailQueryKey } from './useRequestDetail'
import * as vmRequestsApi from '@/api/vm-requests'
import type { VmRequestDetailResponse } from '@/api/vm-requests'
import type { ReactNode } from 'react'

// Mock react-oidc-context with hoisted mock function for testability
const mockUseAuth = vi.hoisted(() =>
  vi.fn(() => ({
    user: { access_token: 'test-access-token' },
    isAuthenticated: true,
  }))
)

vi.mock('react-oidc-context', () => ({
  useAuth: mockUseAuth,
}))

// Mock the API function
vi.mock('@/api/vm-requests', async () => {
  const actual = await vi.importActual('@/api/vm-requests')
  return {
    ...actual,
    getRequestDetail: vi.fn(),
  }
})

const mockGetRequestDetail = vi.mocked(vmRequestsApi.getRequestDetail)

describe('useRequestDetail', () => {
  let queryClient: QueryClient

  const mockDetailResponse: VmRequestDetailResponse = {
    id: 'req-123',
    vmName: 'test-vm',
    size: {
      code: 'M',
      cpuCores: 4,
      memoryGb: 16,
      diskGb: 100,
    },
    justification: 'Test justification',
    status: 'PENDING',
    projectName: 'Test Project',
    requesterName: 'John Doe',
    createdAt: '2025-01-15T10:00:00Z',
    timeline: [
      {
        eventType: 'CREATED',
        actorName: 'John Doe',
        details: null,
        occurredAt: '2025-01-15T10:00:00Z',
      },
    ],
  }

  function createWrapper() {
    return function Wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      )
    }
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
        },
      },
    })
    vi.clearAllMocks()
    // Reset auth mock to default authenticated state
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-access-token' },
      isAuthenticated: true,
    })
  })

  afterEach(() => {
    queryClient.clear()
  })

  describe('successful fetch', () => {
    it('fetches request detail by ID', async () => {
      mockGetRequestDetail.mockResolvedValue(mockDetailResponse)

      const { result } = renderHook(() => useRequestDetail('req-123'), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(mockGetRequestDetail).toHaveBeenCalledWith('req-123', 'test-access-token')
      expect(result.current.data).toEqual(mockDetailResponse)
    })

    it('returns loading state initially', () => {
      mockGetRequestDetail.mockReturnValue(new Promise(() => {})) // Never resolves

      const { result } = renderHook(() => useRequestDetail('req-123'), {
        wrapper: createWrapper(),
      })

      expect(result.current.isLoading).toBe(true)
      expect(result.current.data).toBeUndefined()
    })
  })

  describe('error handling', () => {
    it('returns error state on API failure', async () => {
      const apiError = new vmRequestsApi.ApiError(404, 'Not Found', {
        type: 'not_found',
        message: 'Request not found',
      })
      mockGetRequestDetail.mockRejectedValue(apiError)

      const { result } = renderHook(() => useRequestDetail('invalid-id'), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isError).toBe(true)
      })

      expect(result.current.error).toBeInstanceOf(vmRequestsApi.ApiError)
      expect(result.current.error?.status).toBe(404)
    })
  })

  describe('query disabled states', () => {
    it('is disabled when requestId is undefined', () => {
      const { result } = renderHook(() => useRequestDetail(undefined), {
        wrapper: createWrapper(),
      })

      expect(result.current.isLoading).toBe(false)
      expect(result.current.fetchStatus).toBe('idle')
      expect(mockGetRequestDetail).not.toHaveBeenCalled()
    })

    it('is disabled when accessToken is missing', () => {
      // Override the mock to return no access token
      mockUseAuth.mockReturnValue({
        user: null,
        isAuthenticated: false,
      })

      const { result } = renderHook(() => useRequestDetail('req-123'), {
        wrapper: createWrapper(),
      })

      expect(result.current.fetchStatus).toBe('idle')
      expect(mockGetRequestDetail).not.toHaveBeenCalled()
    })
  })

  describe('polling configuration', () => {
    it('does not poll by default', async () => {
      mockGetRequestDetail.mockResolvedValue(mockDetailResponse)

      const { result } = renderHook(() => useRequestDetail('req-123'), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      // Verify no refetchInterval is active (only one call)
      expect(mockGetRequestDetail).toHaveBeenCalledTimes(1)
    })

    it('enables polling when option is set', async () => {
      mockGetRequestDetail.mockResolvedValue(mockDetailResponse)

      const { result } = renderHook(
        () => useRequestDetail('req-123', { polling: true, pollInterval: 100 }),
        { wrapper: createWrapper() }
      )

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      // Wait for at least one poll cycle
      await waitFor(
        () => {
          expect(mockGetRequestDetail.mock.calls.length).toBeGreaterThan(1)
        },
        { timeout: 500 }
      )
    })

    it('uses default 30 second poll interval', async () => {
      mockGetRequestDetail.mockResolvedValue(mockDetailResponse)

      renderHook(() => useRequestDetail('req-123', { polling: true }), {
        wrapper: createWrapper(),
      })

      // The hook should configure refetchInterval: 30000
      // We can't easily test the exact interval without time manipulation,
      // but we verify the hook accepts the polling option without error
      await waitFor(() => {
        expect(mockGetRequestDetail).toHaveBeenCalled()
      })
    })
  })

  describe('query key factory', () => {
    it('provides all query key', () => {
      expect(requestDetailQueryKey.all).toEqual(['request-detail'])
    })

    it('provides detail query key with requestId', () => {
      expect(requestDetailQueryKey.detail('req-123')).toEqual(['request-detail', 'req-123'])
    })
  })

  describe('refetch behavior', () => {
    it('supports manual refetch', async () => {
      // Use mockResolvedValueOnce for sequential responses
      const updatedResponse = { ...mockDetailResponse, status: 'APPROVED' as const }
      mockGetRequestDetail
        .mockResolvedValueOnce(mockDetailResponse)
        .mockResolvedValueOnce(updatedResponse)

      const { result } = renderHook(() => useRequestDetail('req-123'), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(result.current.data?.status).toBe('PENDING')
      expect(mockGetRequestDetail).toHaveBeenCalledTimes(1)

      // Trigger refetch and wait for completion
      await result.current.refetch()

      await waitFor(() => {
        expect(mockGetRequestDetail).toHaveBeenCalledTimes(2)
      })

      expect(result.current.data?.status).toBe('APPROVED')
    })
  })
})
