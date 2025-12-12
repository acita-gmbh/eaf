import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useSyncVmStatus } from './useSyncVmStatus'
import { ApiError, type SyncVmStatusResponse } from '@/api/vm-requests'

// Mock useAuth
const mockUseAuth = vi.hoisted(() =>
  vi.fn(() => ({
    user: { access_token: 'test-token' },
    isAuthenticated: true,
  }))
)

vi.mock('react-oidc-context', () => ({
  useAuth: mockUseAuth,
}))

// Mock syncVmStatus API
const mockSyncVmStatus = vi.hoisted(() => vi.fn())

vi.mock('@/api/vm-requests', async () => {
  const actual = await vi.importActual('@/api/vm-requests')
  return {
    ...actual,
    syncVmStatus: mockSyncVmStatus,
  }
})

// Create wrapper with QueryClient
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('useSyncVmStatus', () => {
  const testRequestId = 'request-123'
  const successResponse: SyncVmStatusResponse = {
    type: 'synced',
    requestId: testRequestId,
    powerState: 'POWERED_ON',
    ipAddress: '192.168.1.100',
    message: 'VM status synced successfully',
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
      isAuthenticated: true,
    })
  })

  it('calls syncVmStatus API with correct parameters', async () => {
    mockSyncVmStatus.mockResolvedValue(successResponse)

    const { result } = renderHook(() => useSyncVmStatus(testRequestId), {
      wrapper: createWrapper(),
    })

    result.current.mutate()

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockSyncVmStatus).toHaveBeenCalledWith(testRequestId, 'test-token')
  })

  it('throws error when not authenticated', async () => {
    mockUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
    })

    const { result } = renderHook(() => useSyncVmStatus(testRequestId), {
      wrapper: createWrapper(),
    })

    result.current.mutate()

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error?.message).toBe('Not authenticated')
  })

  it('calls onSuccess callback on successful sync', async () => {
    mockSyncVmStatus.mockResolvedValue(successResponse)
    const onSuccess = vi.fn()

    const { result } = renderHook(
      () => useSyncVmStatus(testRequestId, { onSuccess }),
      { wrapper: createWrapper() }
    )

    result.current.mutate()

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(onSuccess).toHaveBeenCalledWith(successResponse)
  })

  it('calls onError callback for ApiError', async () => {
    const apiError = new ApiError(502, 'Bad Gateway', {
      type: 'hypervisor_error',
      message: 'Unable to connect to vCenter',
    })
    mockSyncVmStatus.mockRejectedValue(apiError)
    const onError = vi.fn()

    const { result } = renderHook(
      () => useSyncVmStatus(testRequestId, { onError }),
      { wrapper: createWrapper() }
    )

    result.current.mutate()

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(onError).toHaveBeenCalledWith(apiError)
  })

  it('logs non-ApiError exceptions to console', async () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const networkError = new TypeError('Failed to fetch')
    mockSyncVmStatus.mockRejectedValue(networkError)
    const onError = vi.fn()

    const { result } = renderHook(
      () => useSyncVmStatus(testRequestId, { onError }),
      { wrapper: createWrapper() }
    )

    result.current.mutate()

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    // onError should NOT be called for non-ApiError
    expect(onError).not.toHaveBeenCalled()
    // But console.error should be called
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      'Unexpected error syncing VM status:',
      networkError
    )

    consoleErrorSpy.mockRestore()
  })

  it('invalidates request-detail query on success', async () => {
    mockSyncVmStatus.mockResolvedValue(successResponse)

    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    })

    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )

    const { result } = renderHook(() => useSyncVmStatus(testRequestId), {
      wrapper,
    })

    result.current.mutate()

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['request-detail', testRequestId],
    })
  })

  it('provides isPending state during mutation', async () => {
    let resolvePromise: (value: SyncVmStatusResponse) => void
    const promise = new Promise<SyncVmStatusResponse>((resolve) => {
      resolvePromise = resolve
    })
    mockSyncVmStatus.mockReturnValue(promise)

    const { result } = renderHook(() => useSyncVmStatus(testRequestId), {
      wrapper: createWrapper(),
    })

    expect(result.current.isPending).toBe(false)

    result.current.mutate()

    await waitFor(() => {
      expect(result.current.isPending).toBe(true)
    })

    resolvePromise!(successResponse)

    await waitFor(() => {
      expect(result.current.isPending).toBe(false)
      expect(result.current.isSuccess).toBe(true)
    })
  })

  it('handles 409 NotProvisioned error', async () => {
    const apiError = new ApiError(409, 'Conflict', {
      type: 'not_provisioned',
      message: 'VM has not been provisioned yet',
      requestId: testRequestId,
    })
    mockSyncVmStatus.mockRejectedValue(apiError)
    const onError = vi.fn()

    const { result } = renderHook(
      () => useSyncVmStatus(testRequestId, { onError }),
      { wrapper: createWrapper() }
    )

    result.current.mutate()

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(onError).toHaveBeenCalledWith(apiError)
    expect(result.current.error?.status).toBe(409)
  })

  it('handles 404 NotFound error', async () => {
    const apiError = new ApiError(404, 'Not Found', {
      type: 'not_found',
      message: 'VM request not found',
    })
    mockSyncVmStatus.mockRejectedValue(apiError)
    const onError = vi.fn()

    const { result } = renderHook(
      () => useSyncVmStatus(testRequestId, { onError }),
      { wrapper: createWrapper() }
    )

    result.current.mutate()

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(onError).toHaveBeenCalledWith(apiError)
    expect(result.current.error?.status).toBe(404)
  })
})
