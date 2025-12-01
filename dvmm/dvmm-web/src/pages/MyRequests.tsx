import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileText, Plus, AlertCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
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
import { EmptyState } from '@/components/empty-states/EmptyState'
import { RequestCard } from '@/components/requests/RequestCard'
import { useMyRequests } from '@/hooks/useMyRequests'

const PAGE_SIZE_OPTIONS = [10, 25, 50] as const

/**
 * Page displaying the current user's VM requests.
 *
 * Features:
 * - Paginated list with configurable page size (10/25/50)
 * - Status badges for visual identification
 * - Cancel action for pending requests
 * - Empty state with CTA to create first request
 */
export function MyRequests() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZE_OPTIONS)[number]>(10)

  const { data, isLoading, isError, error } = useMyRequests({ page, size: pageSize })

  const handlePageSizeChange = (value: string) => {
    const newSize = parseInt(value, 10) as (typeof PAGE_SIZE_OPTIONS)[number]
    setPageSize(newSize)
    setPage(0) // Reset to first page when changing page size
  }

  const handlePageChange = (newPage: number) => {
    if (newPage >= 0 && (!data || newPage < data.totalPages)) {
      setPage(newPage)
    }
  }

  const handleCreateRequest = () => {
    navigate('/requests/new')
  }

  // Loading state
  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader onCreateRequest={handleCreateRequest} />
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
        </div>
      </div>
    )
  }

  // Error state
  if (isError) {
    return (
      <div className="space-y-6">
        <PageHeader onCreateRequest={handleCreateRequest} />
        <div
          className="flex flex-col items-center justify-center py-12 text-center"
          data-testid="my-requests-error"
        >
          <AlertCircle className="h-12 w-12 text-destructive mb-4" />
          <h2 className="text-lg font-semibold mb-2">Error Loading</h2>
          <p className="text-muted-foreground mb-4">
            {error?.message || 'Could not load requests.'}
          </p>
          <Button variant="outline" onClick={() => window.location.reload()}>
            Try Again
          </Button>
        </div>
      </div>
    )
  }

  // Empty state
  if (!data || data.items.length === 0) {
    return (
      <div className="space-y-6">
        <PageHeader onCreateRequest={handleCreateRequest} />
        <EmptyState
          icon={FileText}
          title="No Requests"
          description="You haven't submitted any VM requests yet. Create your first request to get started."
          ctaLabel="Request New VM"
          onCtaClick={handleCreateRequest}
        />
      </div>
    )
  }

  // Generate page numbers for pagination
  const pageNumbers = generatePageNumbers(page, data.totalPages)

  return (
    <div className="space-y-6" data-testid="my-requests-page">
      <PageHeader onCreateRequest={handleCreateRequest} />

      {/* Page size selector */}
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {data.totalElements}{' '}
          {data.totalElements === 1 ? 'request' : 'requests'} total
        </p>
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
      </div>

      {/* Request cards */}
      <div className="space-y-4" data-testid="requests-list">
        {data.items.map((request) => (
          <RequestCard key={request.id} request={request} />
        ))}
      </div>

      {/* Pagination */}
      {data.totalPages > 1 && (
        <Pagination data-testid="pagination">
          <PaginationContent>
            <PaginationItem>
              <PaginationPrevious
                onClick={() => handlePageChange(page - 1)}
                aria-disabled={!data.hasPrevious}
                className={!data.hasPrevious ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
              />
            </PaginationItem>

            {pageNumbers.map((pageNum, index) =>
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
                aria-disabled={!data.hasNext}
                className={!data.hasNext ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
              />
            </PaginationItem>
          </PaginationContent>
        </Pagination>
      )}
    </div>
  )
}

interface PageHeaderProps {
  onCreateRequest: () => void
}

function PageHeader({ onCreateRequest }: PageHeaderProps) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold">My Requests</h1>
        <p className="text-muted-foreground">
          Overview of your VM requests and their status
        </p>
      </div>
      <Button size="lg" className="gap-2" onClick={onCreateRequest}>
        <Plus className="h-5 w-5" />
        Request New VM
      </Button>
    </div>
  )
}

/**
 * Generates page numbers for pagination with ellipsis.
 *
 * Shows: first page, last page, current page, and 1-2 pages around current.
 * Uses -1 to represent ellipsis.
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
    pages.push(-1) // ellipsis
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
    pages.push(-1) // ellipsis
  }

  // Always show last page
  if (!pages.includes(totalPages - 1)) {
    pages.push(totalPages - 1)
  }

  return pages
}
