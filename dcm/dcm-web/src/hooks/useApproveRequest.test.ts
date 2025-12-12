import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useApproveRequest } from './useApproveRequest'
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
    approveRequest: vi.fn(),
  }
})

import { useAuth } from 'react-oidc-context'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { approveRequest } from '@/api/admin'

const mockUseAuth = vi.mocked(useAuth)
const mockUseNavigate = vi.mocked(useNavigate)
const mockApproveRequest = vi.mocked(approveRequest)

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

describe('useApproveRequest', () => {
  const requestId = 'req-123'
  const version = 1

  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
    } as ReturnType<typeof useAuth>)
    mockUseNavigate.mockReturnValue(vi.fn())
  })

  it('calls approveRequest with id, version, and token', async () => {
    const mockResponse = { requestId: 'req-123', status: 'APPROVED' }
    mockApproveRequest.mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useApproveRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(version)
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockApproveRequest).toHaveBeenCalledWith(requestId, version, 'test-token')
    expect(result.current.data).toEqual(mockResponse)
  })

  it('shows success toast and navigates on success', async () => {
    const mockNavigate = vi.fn()
    mockUseNavigate.mockReturnValue(mockNavigate)
    mockApproveRequest.mockResolvedValue({ requestId: 'req-123', status: 'APPROVED' })

    const { result } = renderHook(() => useApproveRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(version)
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(toast.success).toHaveBeenCalledWith('Request approved!', {
      description: 'The VM request has been approved for provisioning.',
    })
    expect(mockNavigate).toHaveBeenCalledWith('/admin/requests')
  })

  it('throws ApiError when not authenticated', async () => {
    mockUseAuth.mockReturnValue({
      user: null,
    } as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useApproveRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(version)
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

    const { result } = renderHook(() => useApproveRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(version)
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
    mockApproveRequest.mockRejectedValue(apiError)

    const { result } = renderHook(() => useApproveRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(version)
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBe(apiError)
    expect(result.current.error?.status).toBe(409)
  })

  it('propagates 422 Unprocessable Entity error correctly', async () => {
    const apiError = new ApiError(422, 'Unprocessable Entity', {
      message: 'Request is not in PENDING state',
    })
    mockApproveRequest.mockRejectedValue(apiError)

    const { result } = renderHook(() => useApproveRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(version)
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
    mockApproveRequest.mockReturnValue(pendingPromise as Promise<never>)

    const { result } = renderHook(() => useApproveRequest(requestId), {
      wrapper: createWrapper(),
    })

    expect(result.current.isPending).toBe(false)

    act(() => {
      result.current.mutate(version)
    })

    await waitFor(() => {
      expect(result.current.isPending).toBe(true)
    })

    // Resolve to clean up
    resolvePromise!({ requestId: 'req-123', status: 'APPROVED' })
  })

  it('calls onError callback when mutation fails', async () => {
    const apiError = new ApiError(500, 'Internal Server Error', {
      message: 'Something went wrong',
    })
    mockApproveRequest.mockRejectedValue(apiError)

    const onError = vi.fn()

    const { result } = renderHook(() => useApproveRequest(requestId), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(version, { onError })
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(onError).toHaveBeenCalled()
    const [error, variables] = onError.mock.calls[0]
    expect(error).toBe(apiError)
    expect(variables).toBe(version)
  })
})
