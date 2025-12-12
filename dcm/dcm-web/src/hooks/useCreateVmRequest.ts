import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import {
  createVmRequest,
  ApiError,
  type CreateVmRequestPayload,
  type VmRequestResponse,
} from '@/api/vm-requests'

/**
 * Hook for creating a new VM request.
 *
 * Wraps the createVmRequest API call in a TanStack Query mutation,
 * providing loading, error, and success states.
 *
 * @example
 * ```tsx
 * const { mutate, isPending, isError } = useCreateVmRequest()
 *
 * const handleSubmit = (data: CreateVmRequestPayload) => {
 *   mutate(data, {
 *     onSuccess: (result) => {
 *       toast.success('Request submitted!')
 *       navigate(`/requests/${result.id}`)
 *     },
 *   })
 * }
 * ```
 */
export function useCreateVmRequest() {
  const auth = useAuth()
  const queryClient = useQueryClient()

  return useMutation<VmRequestResponse, ApiError, CreateVmRequestPayload>({
    mutationFn: async (payload: CreateVmRequestPayload) => {
      const accessToken = auth.user?.access_token
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return createVmRequest(payload, accessToken)
    },
    onSuccess: () => {
      // Invalidate related queries to refetch data (fire-and-forget)
      void queryClient.invalidateQueries({ queryKey: ['my-requests'] })
      void queryClient.invalidateQueries({ queryKey: ['vm-requests'] })
    },
  })
}
