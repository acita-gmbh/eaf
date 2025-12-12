import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import {
  getVmwareConfig,
  saveVmwareConfig,
  testVmwareConnection,
  checkVmwareConfigExists,
  type VmwareConfig,
  type SaveVmwareConfigRequest,
  type TestConnectionRequest,
  type SaveConfigResponse,
  type ConnectionTestResult,
  type ConnectionTestError,
  type ConfigExistsResponse,
} from '@/api/vmware-config'
import { ApiError } from '@/api/vm-requests'

/**
 * Query key for VMware configuration.
 */
export const VMWARE_CONFIG_QUERY_KEY = ['vmware-config'] as const

/**
 * Query key for VMware configuration existence check.
 */
export const VMWARE_CONFIG_EXISTS_QUERY_KEY = ['vmware-config', 'exists'] as const

/**
 * Hook for fetching VMware configuration.
 *
 * Story 3.1 AC-3.1.1: Get saved configuration
 *
 * @returns Query result with configuration or null
 */
export function useVmwareConfig() {
  const auth = useAuth()

  return useQuery<VmwareConfig | null, ApiError>({
    queryKey: VMWARE_CONFIG_QUERY_KEY,
    queryFn: async () => {
      const accessToken = auth.user?.access_token
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return getVmwareConfig(accessToken)
    },
    enabled: !!auth.user?.access_token,
    // Don't retry on 404 (not configured)
    retry: (failureCount, error) => {
      if (error.status === 404) return false
      return failureCount < 3
    },
  })
}

/**
 * Hook for saving VMware configuration.
 *
 * Story 3.1 AC-3.1.1: Save vCenter settings
 * Story 3.1 AC-3.1.4: Password encrypted before storage
 *
 * @example
 * ```tsx
 * const { mutate, isPending } = useSaveVmwareConfig()
 *
 * mutate(configData, {
 *   onSuccess: (result) => {
 *     toast.success('Configuration saved!')
 *   },
 *   onError: (error) => {
 *     if (error.status === 409) {
 *       toast.error('Configuration was modified by another admin')
 *     }
 *   },
 * })
 * ```
 */
export function useSaveVmwareConfig() {
  const auth = useAuth()
  const queryClient = useQueryClient()

  return useMutation<SaveConfigResponse, ApiError, SaveVmwareConfigRequest>({
    mutationFn: async (data: SaveVmwareConfigRequest) => {
      const accessToken = auth.user?.access_token
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return saveVmwareConfig(data, accessToken)
    },
    onSuccess: () => {
      // Invalidate queries to refetch updated config
      void queryClient.invalidateQueries({ queryKey: VMWARE_CONFIG_QUERY_KEY })
      void queryClient.invalidateQueries({ queryKey: VMWARE_CONFIG_EXISTS_QUERY_KEY })
    },
  })
}

/**
 * Hook for testing VMware connection.
 *
 * Story 3.1 AC-3.1.2: "Test Connection" validates connectivity
 * Story 3.1 AC-3.1.3: Real-time feedback on validation errors
 *
 * @example
 * ```tsx
 * const { mutate, isPending } = useTestVmwareConnection()
 *
 * mutate(testData, {
 *   onSuccess: (result) => {
 *     if (result.success) {
 *       toast.success(`Connected to vCenter ${result.vcenterVersion}`)
 *     } else {
 *       toast.error(`Connection failed: ${result.message}`)
 *     }
 *   },
 * })
 * ```
 */
export function useTestVmwareConnection() {
  const auth = useAuth()
  const queryClient = useQueryClient()

  return useMutation<
    ConnectionTestResult | ConnectionTestError,
    ApiError,
    TestConnectionRequest
  >({
    mutationFn: async (data: TestConnectionRequest) => {
      const accessToken = auth.user?.access_token
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return testVmwareConnection(data, accessToken)
    },
    onSuccess: (result) => {
      // If test succeeded and updateVerifiedAt was requested, invalidate config
      if (result.success) {
        void queryClient.invalidateQueries({ queryKey: VMWARE_CONFIG_QUERY_KEY })
      }
    },
  })
}

/**
 * Hook for checking if VMware configuration exists.
 *
 * Story 3.1 AC-3.1.5: Lightweight check for warning banner
 *
 * @returns Query result with existence status
 */
export function useVmwareConfigExists() {
  const auth = useAuth()

  return useQuery<ConfigExistsResponse, ApiError>({
    queryKey: VMWARE_CONFIG_EXISTS_QUERY_KEY,
    queryFn: async () => {
      const accessToken = auth.user?.access_token
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return checkVmwareConfigExists(accessToken)
    },
    enabled: !!auth.user?.access_token,
    // Cache for 5 minutes - config doesn't change often
    staleTime: 5 * 60 * 1000,
  })
}
