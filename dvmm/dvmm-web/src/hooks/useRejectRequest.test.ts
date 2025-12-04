import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useRejectRequest, type RejectRequestParams } from './useRejectRequest'
import { ApiError } from '@/api/vm-requests'

// Mock react-oidc-context
vi.mock('react-oidc-context', () => ({
  useAuth: vi.fn(() => ({
    user: { access_token: 'test-token' },
  })),
}))

// Mock react-router-dom
vi.mock('react-router-dom', () => ({
  useNavigate: vi.fn(() => vi.fn()),
}))

// Mock sonner
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

// Mock admin API
vi.mock('@/api/admin', async () => {
  return {
    rejectRequest: vi.fn(),
  }
})

import { useAuth } from 'react-oidc-context'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { rejectRequest } from '@/api/admin'

const mockUseAuth = vi.mocked(useAuth)
const mockUseNavigate = vi.mocked(useNavigate)
const mockRejectRequest = vi.mocked(rejectRequest)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return function Wrapper({ children }: Readonly<{ children: React.ReactNode }>) {
    return createElement(QueryClientProvider, { client: queryClient }, children)
  }
}

describe('useRejectRequest', () => {
  const requestId = 'req-123'
  const rejectParams: RejectRequestParams = {
    version: 1,
    reason: 'Budget constraints prevent approval at this time.',
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
    } as ReturnType<typeof useAuth>)
    mockUseNavigate.mockReturnValue(vi.fn())
  })

  it('calls rejectRequest with id, version, reason, and token', async () => {
    const mockResponse = { requestId: 'req-123', status: 'REJECTED' }
    mockRejectRequest.mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useRejectRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(rejectParams)
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockRejectRequest).toHaveBeenCalledWith(
      requestId,
      rejectParams.version,
      rejectParams.reason,
      'test-token'
    )
    expect(result.current.data).toEqual(mockResponse)
  })

  it('shows success toast and navigates on success', async () => {
    const mockNavigate = vi.fn()
    mockUseNavigate.mockReturnValue(mockNavigate)
    mockRejectRequest.mockResolvedValue({ requestId: 'req-123', status: 'REJECTED' })

    const { result } = renderHook(() => useRejectRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(rejectParams)
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(toast.success).toHaveBeenCalledWith('Request rejected', {
      description: 'The VM request has been rejected.',
    })
    expect(mockNavigate).toHaveBeenCalledWith('/admin/requests')
  })

  it('throws ApiError when not authenticated', async () => {
    mockUseAuth.mockReturnValue({
      user: null,
    } as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useRejectRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(rejectParams)
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBeInstanceOf(ApiError)
    expect(result.current.error?.status).toBe(401)
  })

  it('throws ApiError when access_token is undefined', async () => {
    mockUseAuth.mockReturnValue({
      user: { access_token: undefined },
    } as unknown as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useRejectRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(rejectParams)
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBeInstanceOf(ApiError)
    expect(result.current.error?.status).toBe(401)
  })

  it('propagates 409 Conflict error correctly', async () => {
    const apiError = new ApiError(409, 'Conflict', {
      message: 'Request was modified by another admin',
    })
    mockRejectRequest.mockRejectedValue(apiError)

    const { result } = renderHook(() => useRejectRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(rejectParams)
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBe(apiError)
    expect(result.current.error?.status).toBe(409)
  })

  it('propagates 422 Unprocessable Entity error correctly', async () => {
    const apiError = new ApiError(422, 'Unprocessable Entity', {
      message: 'Reason must be 10-500 characters',
    })
    mockRejectRequest.mockRejectedValue(apiError)

    const { result } = renderHook(() => useRejectRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(rejectParams)
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBe(apiError)
    expect(result.current.error?.status).toBe(422)
  })

  it('sets isPending during mutation', async () => {
    let resolvePromise: (value: unknown) => void
    const pendingPromise = new Promise((resolve) => {
      resolvePromise = resolve
    })
    mockRejectRequest.mockReturnValue(pendingPromise as Promise<never>)

    const { result } = renderHook(() => useRejectRequest(requestId), {
      wrapper: createWrapper(),
    })

    expect(result.current.isPending).toBe(false)

    act(() => {
      result.current.mutate(rejectParams)
    })

    await waitFor(() => {
      expect(result.current.isPending).toBe(true)
    })

    // Resolve to clean up
    resolvePromise!({ requestId: 'req-123', status: 'REJECTED' })
  })

  it('calls onError callback when mutation fails', async () => {
    const apiError = new ApiError(500, 'Internal Server Error', {
      message: 'Something went wrong',
    })
    mockRejectRequest.mockRejectedValue(apiError)

    const onError = vi.fn()

    const { result } = renderHook(() => useRejectRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(rejectParams, { onError })
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(onError).toHaveBeenCalled()
    const [error, variables] = onError.mock.calls[0]
    expect(error).toBe(apiError)
    expect(variables).toEqual(rejectParams)
  })
})
