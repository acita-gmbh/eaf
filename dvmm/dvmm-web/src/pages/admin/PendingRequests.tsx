import { useState } from 'react'
import { AlertCircle, Inbox } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination'
import { AdminQueueEmptyState } from '@/components/empty-states/AdminQueueEmptyState'
import { EmptyState } from '@/components/empty-states/EmptyState'
import { PendingRequestsTable } from '@/components/admin/PendingRequestsTable'
import { ProjectFilter } from '@/components/admin/ProjectFilter'
import { VmwareConfigWarning } from '@/components/admin/VmwareConfigWarning'
import { usePendingRequests } from '@/hooks/usePendingRequests'
import { useProjects } from '@/hooks/useProjects'

const PAGE_SIZE_OPTIONS = [10, 25, 50] as const

/**
 * Admin page displaying pending VM requests for approval.
 *
 * Story 2.9: Admin Approval Queue
 *
 * Features:
 * - AC 1: "Open Requests" section with count badge
 * - AC 5: Project filter dropdown
 * - AC 7: Empty state handling
 * - AC 8: Loading states with skeletons
 */
export function PendingRequests() {
  const [projectId, setProjectId] = useState<string | undefined>(undefined)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZE_OPTIONS)[number]>(25)

  const {
    data,
    isLoading,
    isError,
    error,
    refetch,
  } = usePendingRequests({
    projectId,
    page,
    size: pageSize,
  })

  // Get selected project name for filtered empty state
  const { data: projects } = useProjects()
  const selectedProjectName = projectId
    ? projects?.find((p) => p.id === projectId)?.name
    : undefined

  const handleProjectChange = (newProjectId: string | undefined) => {
    setProjectId(newProjectId)
    setPage(0) // Reset to first page when filter changes
  }

  const handlePageSizeChange = (value: string) => {
    const newSize = parseInt(value, 10) as (typeof PAGE_SIZE_OPTIONS)[number]
    setPageSize(newSize)
    setPage(0)
  }

  const handlePageChange = (newPage: number) => {
    if (newPage >= 0 && (!data || newPage < data.totalPages)) {
      setPage(newPage)
    }
  }

  return (
    <div className="space-y-6" data-testid="pending-requests-page">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-bold">Admin Dashboard</h1>
        <p className="text-muted-foreground">
          Manage pending VM requests across your tenant
        </p>
      </div>

      {/* VMware Configuration Warning (Story 3.1 AC-3.1.5) */}
      <VmwareConfigWarning />

      {/* Open Requests Section */}
      <Card>
        <CardHeader>
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <CardTitle className="flex items-center gap-2">
              Open Requests
              <CountBadge count={data?.totalElements} isLoading={isLoading} />
            </CardTitle>
            <ProjectFilter value={projectId} onChange={handleProjectChange} />
          </div>
        </CardHeader>

        <CardContent>
          {/* Error state */}
          {isError && (
            <ErrorState error={error} onRetry={() => refetch()} />
          )}

          {/* Loading state */}
          {isLoading && !isError && (
            <PendingRequestsTable requests={[]} isLoading={true} />
          )}

          {/* Empty state - No pending requests at all */}
          {!isLoading && !isError && data?.items.length === 0 && !projectId && (
            <AdminQueueEmptyState />
          )}

          {/* Empty state - No results for selected project */}
          {!isLoading && !isError && data?.items.length === 0 && projectId && (
            <EmptyState
              icon={Inbox}
              title={`No requests for ${selectedProjectName || 'selected project'}`}
              description="There are no pending requests for this project."
            />
          )}

          {/* Data state */}
          {!isLoading && !isError && data && data.items.length > 0 && (
            <>
              <PendingRequestsTable requests={data.items} />

              {/* Pagination controls */}
              <div className="mt-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex items-center gap-2">
                  <span className="text-sm text-muted-foreground">Items per page:</span>
                  <Select
                    value={String(pageSize)}
                    onValueChange={handlePageSizeChange}
                  >
                    <SelectTrigger className="w-20" data-testid="page-size-selector">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {PAGE_SIZE_OPTIONS.map((size) => (
                        <SelectItem key={size} value={String(size)}>
                          {size}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {data.totalPages > 1 && (
                  <Pagination data-testid="pagination">
                    <PaginationContent>
                      <PaginationItem>
                        <PaginationPrevious
                          onClick={() => handlePageChange(page - 1)}
                          aria-disabled={page === 0}
                          className={page === 0 ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
                        />
                      </PaginationItem>

                      {generatePageNumbers(page, data.totalPages).map((pageNum, index) =>
                        pageNum === -1 ? (
                          <PaginationItem key={`ellipsis-${index}`}>
                            <span className="px-3 py-2">...</span>
                          </PaginationItem>
                        ) : (
                          <PaginationItem key={pageNum}>
                            <PaginationLink
                              onClick={() => handlePageChange(pageNum)}
                              isActive={pageNum === page}
                              className="cursor-pointer"
                              data-testid={`page-${pageNum + 1}`}
                            >
                              {pageNum + 1}
                            </PaginationLink>
                          </PaginationItem>
                        )
                      )}

                      <PaginationItem>
                        <PaginationNext
                          onClick={() => handlePageChange(page + 1)}
                          aria-disabled={page >= data.totalPages - 1}
                          className={page >= data.totalPages - 1 ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
                        />
                      </PaginationItem>
                    </PaginationContent>
                  </Pagination>
                )}
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

interface CountBadgeProps {
  count: number | undefined
  isLoading: boolean
}

/**
 * Badge showing count of pending requests.
 * Story 2.9 AC 1: Count badge in section header.
 */
function CountBadge({ count, isLoading }: Readonly<CountBadgeProps>) {
  if (isLoading) {
    return <Skeleton className="h-5 w-8 rounded-full" data-testid="count-badge-loading" />
  }

  return (
    <Badge variant="secondary" data-testid="count-badge">
      {count ?? 0}
    </Badge>
  )
}

interface ErrorStateProps {
  error: Error | null
  onRetry: () => void
}

/**
 * Error state display with retry option.
 */
function ErrorState({ error, onRetry }: Readonly<ErrorStateProps>) {
  return (
    <div
      className="flex flex-col items-center justify-center py-12 text-center"
      data-testid="pending-requests-error"
    >
      <AlertCircle className="h-12 w-12 text-destructive mb-4" />
      <h2 className="text-lg font-semibold mb-2">Error Loading Requests</h2>
      <p className="text-muted-foreground mb-4">
        {error?.message || 'Could not load pending requests.'}
      </p>
      <Button variant="outline" onClick={onRetry}>
        Try Again
      </Button>
    </div>
  )
}

/**
 * Generates page numbers for pagination with ellipsis.
 * Same logic as MyRequests.tsx for consistency.
 */
function generatePageNumbers(currentPage: number, totalPages: number): number[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i)
  }

  const pages: number[] = []

  // Always show first page
  pages.push(0)

  // Add ellipsis if current page is far from start
  if (currentPage > 3) {
    pages.push(-1)
  }

  // Pages around current
  const start = Math.max(1, currentPage - 1)
  const end = Math.min(totalPages - 2, currentPage + 1)

  for (let i = start; i <= end; i++) {
    if (!pages.includes(i)) {
      pages.push(i)
    }
  }

  // Add ellipsis if current page is far from end
  if (currentPage < totalPages - 4) {
    pages.push(-1)
  }

  // Always show last page
  if (!pages.includes(totalPages - 1)) {
    pages.push(totalPages - 1)
  }

  return pages
}
