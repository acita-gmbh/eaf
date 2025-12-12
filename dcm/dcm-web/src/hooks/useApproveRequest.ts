import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { toast } from 'sonner'
import { approveRequest } from '@/api/admin'
import { ApiError } from '@/api/vm-requests'

/**
 * Hook for approving a pending VM request.
 *
 * Story 2.11: Approve/Reject Actions
 *
 * Wraps the approveRequest API call in a TanStack Query mutation,
 * providing loading, error, and success states. On success:
 * - Invalidates admin request queries
 * - Shows success toast
 * - Navigates back to approval queue
 *
 * @param id - The request ID to approve
 * @returns Mutation result with approve function
 *
 * @example
 * ```tsx
 * const { mutate, isPending } = useApproveRequest(requestId)
 *
 * const handleApprove = () => {
 *   mutate(version, {
 *     onError: (error) => {
 *       if (error.status === 409) {
 *         toast.error('Request was modified. Please refresh.')
 *       }
 *     },
 *   })
 * }
 * ```
 */
export function useApproveRequest(id: string) {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation<{ requestId: string; status: string }, ApiError, number>({
    mutationFn: async (version: number) => {
      const accessToken = auth.user?.access_token
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return approveRequest(id, version, accessToken)
    },
    onSuccess: () => {
      // Invalidate admin queries to refetch updated lists (fire-and-forget)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'requests', 'pending'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'request', id] })
      toast.success('Request approved!', {
        description: 'The VM request has been approved for provisioning.',
      })
      void navigate('/admin/requests')
    },
  })
}
