import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useCancelRequest } from './useCancelRequest'
import { ApiError } from '@/api/vm-requests'

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

// Mock cancelRequest API
const mockCancelRequest = vi.hoisted(() => vi.fn())

vi.mock('@/api/vm-requests', async () => {
  const actual = await vi.importActual('@/api/vm-requests')
  return {
    ...actual,
    cancelRequest: mockCancelRequest,
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

describe('useCancelRequest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
      isAuthenticated: true,
    })
  })

  it('calls cancelRequest API with correct parameters', async () => {
    mockCancelRequest.mockResolvedValue({ status: 'CANCELLED' })

    const { result } = renderHook(() => useCancelRequest(), {
      wrapper: createWrapper(),
    })

    result.current.mutate({ requestId: 'request-123', reason: 'No longer needed' })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockCancelRequest).toHaveBeenCalledWith(
      'request-123',
      { reason: 'No longer needed' },
      'test-token'
    )
  })

  it('calls cancelRequest without payload when no reason provided', async () => {
    mockCancelRequest.mockResolvedValue({ status: 'CANCELLED' })

    const { result } = renderHook(() => useCancelRequest(), {
      wrapper: createWrapper(),
    })

    result.current.mutate({ requestId: 'request-123' })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockCancelRequest).toHaveBeenCalledWith(
      'request-123',
      undefined,
      'test-token'
    )
  })

  it('throws error when not authenticated', async () => {
    mockUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
    })

    const { result } = renderHook(() => useCancelRequest(), {
      wrapper: createWrapper(),
    })

    result.current.mutate({ requestId: 'request-123' })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBeInstanceOf(ApiError)
    expect(result.current.error?.status).toBe(401)
  })

  it('handles API error correctly', async () => {
    const apiError = new ApiError(409, 'Invalid state', { currentState: 'APPROVED' })
    mockCancelRequest.mockRejectedValue(apiError)

    const { result } = renderHook(() => useCancelRequest(), {
      wrapper: createWrapper(),
    })

    result.current.mutate({ requestId: 'request-123' })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBe(apiError)
  })

  it('invalidates my-requests query on success', async () => {
    mockCancelRequest.mockResolvedValue({ status: 'CANCELLED' })

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

    const { result } = renderHook(() => useCancelRequest(), { wrapper })

    result.current.mutate({ requestId: 'request-123' })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['my-requests'] })
  })

  it('provides isPending state during mutation', async () => {
    let resolvePromise: (value: unknown) => void
    const promise = new Promise((resolve) => {
      resolvePromise = resolve
    })
    mockCancelRequest.mockReturnValue(promise)

    const { result } = renderHook(() => useCancelRequest(), {
      wrapper: createWrapper(),
    })

    expect(result.current.isPending).toBe(false)

    result.current.mutate({ requestId: 'request-123' })

    await waitFor(() => {
      expect(result.current.isPending).toBe(true)
    })

    resolvePromise!({ status: 'CANCELLED' })

    await waitFor(() => {
      expect(result.current.isPending).toBe(false)
      expect(result.current.isSuccess).toBe(true)
    })
  })
})
