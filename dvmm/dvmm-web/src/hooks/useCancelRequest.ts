import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import {
  cancelRequest,
  ApiError,
  type CancelRequestPayload,
  type CancelRequestResponse,
} from '@/api/vm-requests'

/**
 * Parameters for the cancel request mutation.
 */
export interface CancelRequestParams {
  requestId: string
  reason?: string
}

/**
 * Hook for cancelling a VM request.
 *
 * Wraps the cancelRequest API call in a TanStack Query mutation,
 * providing loading, error, and success states.
 *
 * @returns Mutation result with cancel function
 *
 * @example
 * ```tsx
 * const { mutate, isPending, isError } = useCancelRequest()
 *
 * const handleCancel = (requestId: string, reason?: string) => {
 *   mutate({ requestId, reason }, {
 *     onSuccess: () => {
 *       toast.success('Request cancelled')
 *     },
 *     onError: (error) => {
 *       if (isInvalidStateError(error.body)) {
 *         toast.error(`Cannot cancel: ${error.body.currentState}`)
 *       }
 *     },
 *   })
 * }
 * ```
 */
export function useCancelRequest() {
  const auth = useAuth()
  const queryClient = useQueryClient()

  return useMutation<CancelRequestResponse, ApiError, CancelRequestParams>({
    mutationFn: async ({ requestId, reason }: CancelRequestParams) => {
      const accessToken = auth.user?.access_token
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      const payload: CancelRequestPayload | undefined = reason ? { reason } : undefined
      return cancelRequest(requestId, payload, accessToken)
    },
    onSuccess: () => {
      // Invalidate my-requests query to refetch updated list
      queryClient.invalidateQueries({ queryKey: ['my-requests'] })
    },
  })
}
