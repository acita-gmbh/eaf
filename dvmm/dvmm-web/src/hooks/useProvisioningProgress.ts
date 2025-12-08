import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { getProvisioningProgress } from '../api/vm-requests'
import type { VmProvisioningProgressResponse } from '../api/vm-requests'

/**
 * Hook to fetch provisioning progress for a request.
 *
 * @param requestId - The ID of the request
 * @param isProvisioning - Whether the request is currently in PROVISIONING state
 * @returns Query object with progress data
 */
export function useProvisioningProgress(requestId: string, isProvisioning: boolean) {
  const auth = useAuth()

  return useQuery<VmProvisioningProgressResponse>({
    queryKey: ['provisioning-progress', requestId],
    queryFn: () => getProvisioningProgress(requestId, auth.user?.access_token || ''),
    enabled: !!auth.user?.access_token && !!requestId && isProvisioning,
    // Data considered stale after 2.5s (slightly less than poll interval)
    staleTime: 2500,
    // Poll every 3s + jitter to prevent thundering herd across clients
    refetchInterval: () => 3000 + Math.floor(Math.random() * 500),
    // Don't poll in background to save resources
    refetchIntervalInBackground: false,
    // Retry a few times if 404 (might be race condition between status update and progress creation)
    retry: 3,
  })
}
