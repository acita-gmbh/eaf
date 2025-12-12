import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import {
  getRequestDetail,
  ApiError,
  type VmRequestDetailResponse,
} from '@/api/vm-requests'

/**
 * Default polling interval for request detail updates (30 seconds).
 */
const DEFAULT_POLL_INTERVAL = 30000

/**
 * Options for the useRequestDetail hook.
 */
interface UseRequestDetailOptions {
  /** Whether to enable automatic polling for updates. Default: false */
  polling?: boolean
  /** Polling interval in milliseconds. Default: 30000 (30 seconds) */
  pollInterval?: number
}

/**
 * Hook for fetching detailed VM request information with timeline.
 *
 * Wraps the getRequestDetail API call in a TanStack Query,
 * providing loading, error, and cached data states.
 *
 * Supports optional polling for real-time updates on the request detail page.
 * Per AC-4: Polling fetches updates every 30 seconds while page is visible.
 *
 * @param requestId - The ID of the request to fetch
 * @param options - Optional configuration for polling behavior
 * @returns Query result with request detail and timeline
 *
 * @example
 * ```tsx
 * // Basic usage without polling
 * const { data, isLoading, isError } = useRequestDetail('uuid-here')
 *
 * // With polling for real-time updates
 * const { data, isLoading, isError } = useRequestDetail('uuid-here', {
 *   polling: true,
 *   pollInterval: 30000
 * })
 *
 * if (isLoading) return <Loading />
 * if (isError) return <Error />
 *
 * return (
 *   <>
 *     <RequestHeader request={data} />
 *     <Timeline events={data.timeline} />
 *   </>
 * )
 * ```
 */
export function useRequestDetail(
  requestId: string | undefined,
  options: UseRequestDetailOptions = {}
) {
  const auth = useAuth()
  const accessToken = auth.user?.access_token

  const { polling = false, pollInterval = DEFAULT_POLL_INTERVAL } = options

  return useQuery<VmRequestDetailResponse, ApiError>({
    queryKey: ['request-detail', requestId],
    queryFn: async () => {
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      if (!requestId) {
        throw new ApiError(400, 'Bad Request', { message: 'Request ID is required' })
      }
      return getRequestDetail(requestId, accessToken)
    },
    enabled: !!accessToken && !!requestId,
    staleTime: 10000, // 10 seconds - shorter stale time for detail view
    refetchOnWindowFocus: true,
    // Enable polling when specified
    refetchInterval: polling ? pollInterval : false,
    // Only poll when the page is visible
    refetchIntervalInBackground: false,
  })
}

/**
 * Query key factory for request detail queries.
 * Useful for cache invalidation after mutations.
 */
export const requestDetailQueryKey = {
  all: ['request-detail'] as const,
  detail: (requestId: string) => ['request-detail', requestId] as const,
}
