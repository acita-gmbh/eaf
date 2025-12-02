import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import {
  getPendingRequests,
  type GetPendingRequestsParams,
  type PendingRequestsPage,
} from '@/api/admin'
import { ApiError } from '@/api/vm-requests'

/**
 * Hook for fetching pending VM requests for admin approval.
 *
 * Story 2.9: Admin Approval Queue
 *
 * Wraps the getPendingRequests API call in a TanStack Query,
 * providing loading, error, and cached data states.
 *
 * The query only runs when:
 * - User is authenticated (has access token)
 * - User role validation is done by the backend (returns 403 if not admin)
 *
 * @param params - Query parameters (projectId filter, page, size)
 * @returns Query result with paginated pending requests
 *
 * @example
 * ```tsx
 * const { data, isLoading, error } = usePendingRequests({
 *   projectId: selectedProject,
 *   page: 0,
 *   size: 25
 * })
 *
 * if (isLoading) return <Loading />
 * if (error?.status === 403) return <AccessDenied />
 *
 * return <PendingRequestsTable items={data.items} />
 * ```
 */
export function usePendingRequests(params: GetPendingRequestsParams = {}) {
  const auth = useAuth()
  const accessToken = auth.user?.access_token

  // Include all params in queryKey for proper cache invalidation
  const queryParams = {
    projectId: params.projectId,
    page: params.page ?? 0,
    size: params.size ?? 25,
  }

  return useQuery<PendingRequestsPage, ApiError>({
    queryKey: ['admin', 'pending-requests', queryParams],
    queryFn: async () => {
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return getPendingRequests(queryParams, accessToken)
    },
    enabled: !!accessToken,
    staleTime: 30000, // 30 seconds - reasonable for list data
    refetchOnWindowFocus: true,
  })
}
