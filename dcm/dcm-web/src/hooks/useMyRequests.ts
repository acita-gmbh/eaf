import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import {
  getMyRequests,
  ApiError,
  type GetMyRequestsParams,
  type PagedVmRequestsResponse,
} from '@/api/vm-requests'

/**
 * Hook for fetching the current user's VM requests.
 *
 * Wraps the getMyRequests API call in a TanStack Query,
 * providing loading, error, and cached data states.
 *
 * @param params - Pagination parameters (page, size)
 * @returns Query result with paginated VM requests
 *
 * @example
 * ```tsx
 * const { data, isLoading, isError } = useMyRequests({ page: 0, size: 10 })
 *
 * if (isLoading) return <Loading />
 * if (isError) return <Error />
 *
 * return (
 *   <RequestList items={data.items} />
 * )
 * ```
 */
export function useMyRequests(params: GetMyRequestsParams = {}) {
  const auth = useAuth()
  const accessToken = auth.user?.access_token

  // Use full params object in queryKey for cache extensibility
  // when future filters are added (status, search, etc.)
  const queryParams = { page: params.page ?? 0, size: params.size ?? 20 }

  return useQuery<PagedVmRequestsResponse, ApiError>({
    queryKey: ['my-requests', queryParams],
    queryFn: async () => {
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return getMyRequests(params, accessToken)
    },
    enabled: !!accessToken,
    staleTime: 30000, // 30 seconds - reasonable for list data
    refetchOnWindowFocus: true,
  })
}
