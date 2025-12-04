import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import {
  useVmwareConfig,
  useSaveVmwareConfig,
  useTestVmwareConnection,
  useVmwareConfigExists,
} from './useVmwareConfig'
import { ApiError } from '@/api/vm-requests'

// Mock react-oidc-context
vi.mock('react-oidc-context', () => ({
  useAuth: vi.fn(() => ({
    user: { access_token: 'test-token' },
  })),
}))

// Mock vmware-config API
vi.mock('@/api/vmware-config', async () => {
  return {
    getVmwareConfig: vi.fn(),
    saveVmwareConfig: vi.fn(),
    testVmwareConnection: vi.fn(),
    checkVmwareConfigExists: vi.fn(),
    isConnectionTestError: vi.fn((result) => !result.success),
  }
})

import { useAuth } from 'react-oidc-context'
import {
  getVmwareConfig,
  saveVmwareConfig,
  testVmwareConnection,
  checkVmwareConfigExists,
} from '@/api/vmware-config'

const mockUseAuth = vi.mocked(useAuth)
const mockGetVmwareConfig = vi.mocked(getVmwareConfig)
const mockSaveVmwareConfig = vi.mocked(saveVmwareConfig)
const mockTestVmwareConnection = vi.mocked(testVmwareConnection)
const mockCheckVmwareConfigExists = vi.mocked(checkVmwareConfigExists)

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

describe('useVmwareConfig', () => {
  const mockConfig = {
    id: 'config-123',
    vcenterUrl: 'https://vcenter.example.com',
    username: 'admin@vsphere.local',
    hasPassword: true,
    datacenterName: 'DC1',
    clusterName: 'Cluster1',
    datastoreName: 'Datastore1',
    networkName: 'VM Network',
    templateName: 'ubuntu-22.04-template',
    folderPath: '/VMs/DVMM',
    verifiedAt: '2024-01-15T10:00:00Z',
    createdAt: '2024-01-01T09:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
    createdBy: 'user-1',
    updatedBy: 'user-1',
    version: 1,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({
      user: { access_token: 'test-token' },
    } as ReturnType<typeof useAuth>)
  })

  describe('useVmwareConfig', () => {
    it('fetches VMware configuration with token', async () => {
      mockGetVmwareConfig.mockResolvedValue(mockConfig)

      const { result } = renderHook(() => useVmwareConfig(), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(mockGetVmwareConfig).toHaveBeenCalledWith('test-token')
      expect(result.current.data).toEqual(mockConfig)
    })

    it('returns null when configuration does not exist', async () => {
      mockGetVmwareConfig.mockResolvedValue(null)

      const { result } = renderHook(() => useVmwareConfig(), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(result.current.data).toBeNull()
    })

    it('does not fetch when not authenticated', async () => {
      mockUseAuth.mockReturnValue({
        user: null,
      } as ReturnType<typeof useAuth>)

      const { result } = renderHook(() => useVmwareConfig(), {
        wrapper: createWrapper(),
      })

      // Query should be disabled, not loading
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false)
      })

      expect(mockGetVmwareConfig).not.toHaveBeenCalled()
    })

    it('handles API errors correctly', async () => {
      // Using 404 status to test immediate error handling (hook doesn't retry 404)
      // Hook's custom retry function: `retry: (failureCount, error) => error.status === 404 ? false : failureCount < 3`
      const apiError = new ApiError(404, 'Not Found', {
        message: 'Resource not found',
      })
      // Note: 404 from the API function returns null, but we're testing the hook's
      // error handling when the underlying API throws (e.g., network error with 404 status)
      mockGetVmwareConfig.mockRejectedValue(apiError)

      const { result } = renderHook(() => useVmwareConfig(), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isError).toBe(true)
      })

      expect(result.current.error).toBe(apiError)
    })
  })

  describe('useSaveVmwareConfig', () => {
    const saveRequest = {
      vcenterUrl: 'https://vcenter.example.com',
      username: 'admin@vsphere.local',
      password: 'secret123',
      datacenterName: 'DC1',
      clusterName: 'Cluster1',
      datastoreName: 'Datastore1',
      networkName: 'VM Network',
      version: null,
    }

    it('saves configuration with payload and token', async () => {
      const saveResponse = {
        id: 'config-123',
        version: 1,
        message: 'Configuration saved',
      }
      mockSaveVmwareConfig.mockResolvedValue(saveResponse)

      const { result } = renderHook(() => useSaveVmwareConfig(), {
        wrapper: createWrapper(),
      })

      act(() => {
        result.current.mutate(saveRequest)
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(mockSaveVmwareConfig).toHaveBeenCalledWith(saveRequest, 'test-token')
      expect(result.current.data).toEqual(saveResponse)
    })

    it('throws ApiError when not authenticated', async () => {
      mockUseAuth.mockReturnValue({
        user: null,
      } as ReturnType<typeof useAuth>)

      const { result } = renderHook(() => useSaveVmwareConfig(), {
        wrapper: createWrapper(),
      })

      act(() => {
        result.current.mutate(saveRequest)
      })

      await waitFor(() => {
        expect(result.current.isError).toBe(true)
      })

      expect(result.current.error).toBeInstanceOf(ApiError)
      expect(result.current.error?.status).toBe(401)
    })

    it('propagates 409 conflict errors', async () => {
      const conflictError = new ApiError(409, 'Conflict', {
        message: 'Configuration was modified by another admin',
      })
      mockSaveVmwareConfig.mockRejectedValue(conflictError)

      const { result } = renderHook(() => useSaveVmwareConfig(), {
        wrapper: createWrapper(),
      })

      act(() => {
        result.current.mutate({ ...saveRequest, version: 1 })
      })

      await waitFor(() => {
        expect(result.current.isError).toBe(true)
      })

      expect(result.current.error).toBe(conflictError)
      expect(result.current.error?.status).toBe(409)
    })

    it('calls onSuccess callback when provided', async () => {
      const saveResponse = {
        id: 'config-123',
        version: 1,
        message: 'Configuration saved',
      }
      mockSaveVmwareConfig.mockResolvedValue(saveResponse)

      const onSuccess = vi.fn()

      const { result } = renderHook(() => useSaveVmwareConfig(), {
        wrapper: createWrapper(),
      })

      act(() => {
        result.current.mutate(saveRequest, { onSuccess })
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(onSuccess).toHaveBeenCalled()
      const [data, variables] = onSuccess.mock.calls[0]
      expect(data).toEqual(saveResponse)
      expect(variables).toEqual(saveRequest)
    })
  })

  describe('useTestVmwareConnection', () => {
    const testRequest = {
      vcenterUrl: 'https://vcenter.example.com',
      username: 'admin@vsphere.local',
      password: 'secret123',
      datacenterName: 'DC1',
      clusterName: 'Cluster1',
      datastoreName: 'Datastore1',
      networkName: 'VM Network',
    }

    it('tests connection with payload and token', async () => {
      const successResult = {
        success: true,
        vcenterVersion: '8.0.1',
        clusterName: 'Cluster1',
        clusterHosts: 3,
        datastoreFreeGb: 500,
        message: 'Connected',
      }
      mockTestVmwareConnection.mockResolvedValue(successResult)

      const { result } = renderHook(() => useTestVmwareConnection(), {
        wrapper: createWrapper(),
      })

      act(() => {
        result.current.mutate(testRequest)
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(mockTestVmwareConnection).toHaveBeenCalledWith(testRequest, 'test-token')
      expect(result.current.data).toEqual(successResult)
    })

    it('returns error result on connection failure', async () => {
      const errorResult = {
        success: false,
        error: 'CONNECTION_FAILED',
        message: 'Unable to connect',
      }
      mockTestVmwareConnection.mockResolvedValue(errorResult)

      const { result } = renderHook(() => useTestVmwareConnection(), {
        wrapper: createWrapper(),
      })

      act(() => {
        result.current.mutate(testRequest)
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(result.current.data).toEqual(errorResult)
    })

    it('throws ApiError when not authenticated', async () => {
      mockUseAuth.mockReturnValue({
        user: null,
      } as ReturnType<typeof useAuth>)

      const { result } = renderHook(() => useTestVmwareConnection(), {
        wrapper: createWrapper(),
      })

      act(() => {
        result.current.mutate(testRequest)
      })

      await waitFor(() => {
        expect(result.current.isError).toBe(true)
      })

      expect(result.current.error).toBeInstanceOf(ApiError)
      expect(result.current.error?.status).toBe(401)
    })

    it('sets isPending during mutation', async () => {
      let resolvePromise: (value: unknown) => void
      const pendingPromise = new Promise((resolve) => {
        resolvePromise = resolve
      })
      mockTestVmwareConnection.mockReturnValue(
        pendingPromise as Promise<never>
      )

      const { result } = renderHook(() => useTestVmwareConnection(), {
        wrapper: createWrapper(),
      })

      expect(result.current.isPending).toBe(false)

      act(() => {
        result.current.mutate(testRequest)
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(true)
      })

      // Resolve to clean up
      resolvePromise!({
        success: true,
        vcenterVersion: '8.0.1',
        clusterName: 'Cluster1',
        clusterHosts: 3,
        datastoreFreeGb: 500,
        message: 'Connected',
      })
    })
  })

  describe('useVmwareConfigExists', () => {
    it('checks if configuration exists', async () => {
      const existsResponse = {
        exists: true,
        verifiedAt: '2024-01-15T10:00:00Z',
      }
      mockCheckVmwareConfigExists.mockResolvedValue(existsResponse)

      const { result } = renderHook(() => useVmwareConfigExists(), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(mockCheckVmwareConfigExists).toHaveBeenCalledWith('test-token')
      expect(result.current.data).toEqual(existsResponse)
    })

    it('returns exists false when config does not exist', async () => {
      const existsResponse = {
        exists: false,
        verifiedAt: null,
      }
      mockCheckVmwareConfigExists.mockResolvedValue(existsResponse)

      const { result } = renderHook(() => useVmwareConfigExists(), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true)
      })

      expect(result.current.data?.exists).toBe(false)
      expect(result.current.data?.verifiedAt).toBeNull()
    })

    it('does not fetch when not authenticated', async () => {
      mockUseAuth.mockReturnValue({
        user: null,
      } as ReturnType<typeof useAuth>)

      const { result } = renderHook(() => useVmwareConfigExists(), {
        wrapper: createWrapper(),
      })

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false)
      })

      expect(mockCheckVmwareConfigExists).not.toHaveBeenCalled()
    })
  })
})
