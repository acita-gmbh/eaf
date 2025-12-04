import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { toast } from 'sonner'
import { rejectRequest } from '@/api/admin'
import { ApiError } from '@/api/vm-requests'

/**
 * Parameters for the reject request mutation.
 */
export interface RejectRequestParams {
  version: number
  reason: string
}

/**
 * Hook for rejecting a pending VM request.
 *
 * Story 2.11: Approve/Reject Actions
 *
 * Wraps the rejectRequest API call in a TanStack Query mutation,
 * providing loading, error, and success states. On success:
 * - Invalidates admin request queries
 * - Shows success toast
 * - Navigates back to approval queue
 *
 * @param id - The request ID to reject
 * @returns Mutation result with reject function
 *
 * @example
 * ```tsx
 * const { mutate, isPending } = useRejectRequest(requestId)
 *
 * const handleReject = (reason: string) => {
 *   mutate({ version, reason }, {
 *     onError: (error) => {
 *       if (error.status === 409) {
 *         toast.error('Request was modified. Please refresh.')
 *       }
 *     },
 *   })
 * }
 * ```
 */
export function useRejectRequest(id: string) {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation<
    { requestId: string; status: string },
    ApiError,
    RejectRequestParams
  >({
    mutationFn: async ({ version, reason }: RejectRequestParams) => {
      const accessToken = auth.user?.access_token
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return rejectRequest(id, version, reason, accessToken)
    },
    onSuccess: () => {
      // Invalidate admin queries to refetch updated lists (fire-and-forget)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'requests', 'pending'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'request', id] })
      toast.success('Request rejected', {
        description: 'The VM request has been rejected.',
      })
      void navigate('/admin/requests')
    },
  })
}
