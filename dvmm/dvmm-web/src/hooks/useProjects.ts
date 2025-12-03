import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { getProjects, type Project } from '@/api/admin'
import { ApiError } from '@/api/vm-requests'

/**
 * Hook for fetching distinct projects for the filter dropdown.
 *
 * Story 2.9: Admin Approval Queue (AC 5)
 *
 * Wraps the getProjects API call in a TanStack Query,
 * providing loading, error, and cached data states.
 *
 * The query only runs when the user is authenticated.
 * Backend returns 403 if user is not an admin.
 *
 * @returns Query result with list of projects
 *
 * @example
 * ```tsx
 * const { data: projects, isLoading } = useProjects()
 *
 * if (isLoading) return <Skeleton />
 *
 * return (
 *   <Select>
 *     {projects?.map(p => (
 *       <SelectItem key={p.id} value={p.id}>{p.name}</SelectItem>
 *     ))}
 *   </Select>
 * )
 * ```
 */
export function useProjects() {
  const auth = useAuth()
  const accessToken = auth.user?.access_token

  return useQuery<Project[], ApiError>({
    queryKey: ['admin', 'projects'],
    queryFn: async () => {
      if (!accessToken) {
        throw new ApiError(401, 'Unauthorized', { message: 'Not authenticated' })
      }
      return getProjects(accessToken)
    },
    enabled: !!accessToken,
    staleTime: 60000, // 1 minute - projects don't change often
    refetchOnWindowFocus: false, // No need to refetch on focus
  })
}
