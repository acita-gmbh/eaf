/**
 * Hook for syncing VM status from vSphere.
 *
 * Story 3-7: Provides mutation to refresh VM runtime details.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { syncVmStatus, type SyncVmStatusResponse, ApiError } from '@/api/vm-requests'

interface UseSyncVmStatusOptions {
  /** Callback when sync succeeds */
  onSuccess?: (data: SyncVmStatusResponse) => void
  /** Callback when sync fails */
  onError?: (error: ApiError) => void
}

/**
 * Hook for syncing VM status from vSphere.
 *
 * Invalidates the request detail query on success to refresh the UI
 * with the latest VM details.
 *
 * @param requestId - The ID of the request to sync
 * @param options - Optional success/error callbacks
 * @returns Mutation object with mutate function and state
 */
export function useSyncVmStatus(requestId: string, options?: UseSyncVmStatusOptions) {
  const auth = useAuth()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async () => {
      const token = auth.user?.access_token
      if (!token) {
        throw new Error('Not authenticated')
      }
      return syncVmStatus(requestId, token)
    },
    onSuccess: (data) => {
      // Invalidate the request detail query to refresh VM details
      void queryClient.invalidateQueries({
        queryKey: ['request-detail', requestId],
      })
      options?.onSuccess?.(data)
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        options?.onError?.(error)
      } else {
        // Log non-ApiError exceptions for debugging (e.g., network failures)
        console.error('Unexpected error syncing VM status:', error)
      }
    },
  })
}
