import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useCreateVmRequest } from './useCreateVmRequest'
import { ApiError, type CreateVmRequestPayload } from '@/api/vm-requests'

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
    createVmRequest: vi.fn(),
  }
})

import { useAuth } from 'react-oidc-context'
import { createVmRequest } from '@/api/vm-requests'

const mockUseAuth = vi.mocked(useAuth)
const mockCreateVmRequest = vi.mocked(createVmRequest)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children)
  }
}

describe('useCreateVmRequest', () => {
  const validPayload: CreateVmRequestPayload = {
    vmName: 'test-vm',
    projectId: '123e4567-e89b-12d3-a456-426614174000',
    size: 'M',
    justification: 'This is a test justification for the VM request.',
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
    } as ReturnType<typeof useAuth>)
  })

  it('calls createVmRequest with payload and token', async () => {
    const mockResponse = {
      id: 'new-request-id',
      vmName: 'test-vm',
      projectId: validPayload.projectId,
      projectName: null,
      size: { code: 'M', cpuCores: 4, memoryGb: 16, diskGb: 100 },
      status: 'PENDING' as const,
      createdAt: '2024-01-01T00:00:00Z',
    }
    mockCreateVmRequest.mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useCreateVmRequest(), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(validPayload)
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockCreateVmRequest).toHaveBeenCalledWith(validPayload, 'test-token')
    expect(result.current.data).toEqual(mockResponse)
  })

  it('throws ApiError when not authenticated', async () => {
    mockUseAuth.mockReturnValue({
      user: null,
    } as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useCreateVmRequest(), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(validPayload)
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
    } as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useCreateVmRequest(), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(validPayload)
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBeInstanceOf(ApiError)
    expect(result.current.error?.status).toBe(401)
  })

  it('propagates API errors correctly', async () => {
    const apiError = new ApiError(400, 'Bad Request', {
      type: 'validation',
      errors: [{ field: 'vmName', message: 'Invalid name' }],
    })
    mockCreateVmRequest.mockRejectedValue(apiError)

    const { result } = renderHook(() => useCreateVmRequest(), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(validPayload)
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toBe(apiError)
    expect(result.current.error?.status).toBe(400)
  })

  it('sets isPending during mutation', async () => {
    let resolvePromise: (value: unknown) => void
    const pendingPromise = new Promise((resolve) => {
      resolvePromise = resolve
    })
    mockCreateVmRequest.mockReturnValue(pendingPromise as Promise<never>)

    const { result } = renderHook(() => useCreateVmRequest(), {
      wrapper: createWrapper(),
    })

    expect(result.current.isPending).toBe(false)

    act(() => {
      result.current.mutate(validPayload)
    })

    await waitFor(() => {
      expect(result.current.isPending).toBe(true)
    })

    // Resolve to clean up
    resolvePromise!({
      id: 'id',
      vmName: 'test',
      projectId: 'proj',
      projectName: null,
      size: { code: 'M', cpuCores: 4, memoryGb: 16, diskGb: 100 },
      status: 'PENDING',
      createdAt: '2024-01-01T00:00:00Z',
    })
  })

  it('calls onSuccess callback when provided', async () => {
    const mockResponse = {
      id: 'new-request-id',
      vmName: 'test-vm',
      projectId: validPayload.projectId,
      projectName: null,
      size: { code: 'M', cpuCores: 4, memoryGb: 16, diskGb: 100 },
      status: 'PENDING' as const,
      createdAt: '2024-01-01T00:00:00Z',
    }
    mockCreateVmRequest.mockResolvedValue(mockResponse)

    const onSuccess = vi.fn()

    const { result } = renderHook(() => useCreateVmRequest(), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(validPayload, { onSuccess })
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    // Verify onSuccess was called with correct data and variables
    expect(onSuccess).toHaveBeenCalled()
    const [data, variables] = onSuccess.mock.calls[0]
    expect(data).toEqual(mockResponse)
    expect(variables).toEqual(validPayload)
  })

  it('calls onError callback when mutation fails', async () => {
    const apiError = new ApiError(500, 'Internal Server Error', {
      message: 'Something went wrong',
    })
    mockCreateVmRequest.mockRejectedValue(apiError)

    const onError = vi.fn()

    const { result } = renderHook(() => useCreateVmRequest(), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate(validPayload, { onError })
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    // Verify onError was called with correct error and variables
    expect(onError).toHaveBeenCalled()
    const [error, variables] = onError.mock.calls[0]
    expect(error).toBe(apiError)
    expect(variables).toEqual(validPayload)
  })
})
