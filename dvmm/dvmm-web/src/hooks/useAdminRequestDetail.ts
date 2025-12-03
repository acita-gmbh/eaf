import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import {
  getAdminRequestDetail,
  type AdminRequestDetail,
} from '@/api/admin'
import { ApiError } from '@/api/vm-requests'

/**
 * Default polling interval for admin request detail updates (30 seconds).
 */
const DEFAULT_POLL_INTERVAL = 30000
/**
 * Jitter range in milliseconds (0-5 seconds).
 * Prevents "thundering herd" when multiple admins poll simultaneously.
 * Per CLAUDE.md TanStack Query Polling Patterns.
 */
const POLL_JITTER = 5000

/**
 * Options for the useAdminRequestDetail hook.
 */
interface UseAdminRequestDetailOptions {
  /** Whether to enable automatic polling for updates. Default: false */
  polling?: boolean
  /** Polling interval in milliseconds. Default: 30000 (30 seconds) */
  pollInterval?: number
}

/**
 * Hook for fetching detailed VM request information for admin view.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * Wraps the getAdminRequestDetail API call in a TanStack Query,
 * providing loading, error, and cached data states.
 *
 * Includes admin-specific data:
 * - Requester info (name, email, role) [AC 2]
 * - Request details with justification [AC 3]
 * - Timeline events [AC 5]
 * - Requester history (up to 5 recent requests) [AC 6]
 *
 * Supports optional polling for real-time updates on the request detail page.
 * Per AC-4: Polling fetches updates every 30 seconds while page is visible.
 *
 * @param requestId - The ID of the request to fetch
 * @param options - Optional configuration for polling behavior
 * @returns Query result with admin request detail, timeline, and requester history
 *
 * @example
 * ```tsx
 * // Basic usage without polling
 * const { data, isLoading, isError } = useAdminRequestDetail('uuid-here')
 *
 * // With polling for real-time updates
 * const { data, isLoading, isError } = useAdminRequestDetail('uuid-here', {
 *   polling: true,
 *   pollInterval: 30000
 * })
 *
 * if (isLoading) return <Loading />
 * if (isError) return <Error />
 *
 * return (
 *   <>
 *     <RequesterInfo requester={data.requester} />
 *     <RequestDetails request={data} />
 *     <Timeline events={data.timeline} />
 *     <RequesterHistory history={data.requesterHistory} />
 *   </>
 * )
 * ```
 */
export function useAdminRequestDetail(
  requestId: string | undefined,
  options: UseAdminRequestDetailOptions = {}
) {
  const auth = useAuth()
  const accessToken = auth.user?.access_token

  const { polling = false, pollInterval = DEFAULT_POLL_INTERVAL } = options

  // Calculate jitter once on mount to prevent "thundering herd"
  // when multiple admins view the same or different requests.
  // Using useState with lazy initializer ensures stable value across renders.
  const [jitter] = useState(() => Math.floor(Math.random() * POLL_JITTER))
  const pollIntervalWithJitter = pollInterval + jitter

  return useQuery<AdminRequestDetail, ApiError>({
    queryKey: ['admin-request-detail', requestId],
    queryFn: async () => {
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      if (!requestId) {
        throw new ApiError(400, 'Bad Request', { message: 'Request ID is required' })
      }
      return getAdminRequestDetail(requestId, accessToken)
    },
    enabled: !!accessToken && !!requestId,
    staleTime: 10000, // 10 seconds - shorter stale time for detail view
    refetchOnWindowFocus: true,
    // Enable polling when specified, with jitter (30-35s interval)
    refetchInterval: polling ? pollIntervalWithJitter : false,
    // Only poll when the page is visible
    refetchIntervalInBackground: false,
  })
}

/**
 * Query key factory for admin request detail queries.
 * Useful for cache invalidation after mutations (approve/reject).
 */
export const adminRequestDetailQueryKey = {
  all: ['admin-request-detail'] as const,
  detail: (requestId: string) => ['admin-request-detail', requestId] as const,
}
