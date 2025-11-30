import { QueryClient } from '@tanstack/react-query'

/**
 * QueryClient instance for TanStack Query.
 *
 * Configuration follows TanStack Query best practices:
 * - staleTime: How long data is considered fresh (5s for responsiveness)
 * - gcTime: How long to keep unused data in cache (5min)
 * - retry: Number of retries on failure
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 1000, // 5 seconds
      gcTime: 5 * 60 * 1000, // 5 minutes
      retry: 1,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0, // Don't retry mutations - let user retry manually
    },
  },
})
