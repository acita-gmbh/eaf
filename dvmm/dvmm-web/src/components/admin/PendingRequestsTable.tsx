import { useNavigate } from 'react-router-dom'
import { formatDistanceToNow, differenceInHours } from 'date-fns'
import { Clock } from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import type { PendingRequest } from '@/api/admin'

/**
 * Threshold in hours for highlighting old requests.
 * Requests older than this are highlighted with amber background.
 */
const WAITING_THRESHOLD_HOURS = 48

interface PendingRequestsTableProps {
  requests: PendingRequest[]
  isLoading?: boolean
}

/**
 * Table displaying pending VM requests for admin approval.
 *
 * Story 2.9: Admin Approval Queue
 *
 * Features:
 * - AC 2: Columns - Requester, VM Name, Project, Size, Age, Actions
 * - AC 4: Amber highlighting for requests > 48h old
 * - AC 9: Clickable rows navigate to request detail
 * - AC 10: Disabled action buttons with tooltips (prep for Story 2.11)
 */
export function PendingRequestsTable({
  requests,
  isLoading = false,
}: Readonly<PendingRequestsTableProps>) {
  const navigate = useNavigate()

  if (isLoading) {
    return <PendingRequestsTableSkeleton />
  }

  const handleRowClick = (requestId: string) => {
    navigate(`/admin/requests/${requestId}`)
  }

  return (
    <Table data-testid="pending-requests-table">
      <TableHeader>
        <TableRow>
          <TableHead>Requester</TableHead>
          <TableHead>VM Name</TableHead>
          <TableHead>Project</TableHead>
          <TableHead>Size</TableHead>
          <TableHead>Age</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {requests.map((request) => (
          <PendingRequestRow
            key={request.id}
            request={request}
            onClick={() => handleRowClick(request.id)}
          />
        ))}
      </TableBody>
    </Table>
  )
}

interface PendingRequestRowProps {
  request: PendingRequest
  onClick: () => void
}

/**
 * Individual row for a pending request.
 *
 * Highlights requests older than 48 hours with amber background
 * and shows "Waiting long" badge.
 */
function PendingRequestRow({ request, onClick }: Readonly<PendingRequestRowProps>) {
  const createdDate = new Date(request.createdAt)
  const age = formatDistanceToNow(createdDate, { addSuffix: true })
  const hoursOld = differenceInHours(new Date(), createdDate)
  const isWaitingLong = hoursOld > WAITING_THRESHOLD_HOURS

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      onClick()
    }
  }

  // Prevent action button clicks from triggering row navigation
  const handleActionClick = (e: React.MouseEvent) => {
    e.stopPropagation()
  }

  return (
    <TableRow
      data-testid={`pending-request-row-${request.id}`}
      className={cn(
        'cursor-pointer',
        isWaitingLong && 'bg-amber-50 hover:bg-amber-100'
      )}
      onClick={onClick}
      tabIndex={0}
      onKeyDown={handleKeyDown}
      role="button"
      aria-label={`View request for ${request.vmName} by ${request.requesterName}`}
    >
      <TableCell data-testid="requester-name">{request.requesterName}</TableCell>
      <TableCell data-testid="vm-name" className="font-medium">
        {request.vmName}
      </TableCell>
      <TableCell data-testid="project-name">{request.projectName}</TableCell>
      <TableCell data-testid="size">
        <SizeDisplay request={request} />
      </TableCell>
      <TableCell data-testid="age">
        <div className="flex items-center gap-2">
          <span>{age}</span>
          {isWaitingLong && (
            <Badge
              variant="outline"
              className="border-amber-500 text-amber-700 bg-amber-100"
              data-testid="waiting-long-badge"
            >
              <Clock className="h-3 w-3 mr-1" />
              Waiting long
            </Badge>
          )}
        </div>
      </TableCell>
      <TableCell className="text-right" onClick={handleActionClick}>
        <ActionButtons />
      </TableCell>
    </TableRow>
  )
}

/**
 * Displays VM size with tooltip showing full specs.
 */
function SizeDisplay({ request }: Readonly<{ request: PendingRequest }>) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="cursor-help underline decoration-dotted">
          {request.size}
        </span>
      </TooltipTrigger>
      <TooltipContent>
        {request.cpuCores} CPU, {request.memoryGb} GB RAM, {request.diskGb} GB Disk
      </TooltipContent>
    </Tooltip>
  )
}

/**
 * Disabled action buttons with tooltips.
 * Story 2.9 AC 10: Buttons visible but disabled, tooltip explains availability.
 */
function ActionButtons() {
  return (
    <div className="flex justify-end gap-2">
      <Tooltip>
        <TooltipTrigger asChild>
          <span>
            <Button
              variant="outline"
              size="sm"
              disabled
              aria-disabled="true"
              aria-label="Approve request - available in future update"
              data-testid="approve-button"
            >
              Approve
            </Button>
          </span>
        </TooltipTrigger>
        <TooltipContent>Available in Story 2.11</TooltipContent>
      </Tooltip>

      <Tooltip>
        <TooltipTrigger asChild>
          <span>
            <Button
              variant="outline"
              size="sm"
              disabled
              aria-disabled="true"
              aria-label="Reject request - available in future update"
              data-testid="reject-button"
            >
              Reject
            </Button>
          </span>
        </TooltipTrigger>
        <TooltipContent>Available in Story 2.11</TooltipContent>
      </Tooltip>
    </div>
  )
}

/**
 * Skeleton loader matching the table structure.
 * Story 2.9 AC 8: Loading states.
 */
function PendingRequestsTableSkeleton() {
  const skeletonRows = Array.from({ length: 5 }, (_, i) => i)

  return (
    <Table data-testid="pending-requests-table-skeleton">
      <TableHeader>
        <TableRow>
          <TableHead>Requester</TableHead>
          <TableHead>VM Name</TableHead>
          <TableHead>Project</TableHead>
          <TableHead>Size</TableHead>
          <TableHead>Age</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {skeletonRows.map((i) => (
          <TableRow key={i}>
            <TableCell>
              <Skeleton className="h-4 w-24" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-32" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-28" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-10" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-20" />
            </TableCell>
            <TableCell className="text-right">
              <div className="flex justify-end gap-2">
                <Skeleton className="h-8 w-16" />
                <Skeleton className="h-8 w-14" />
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
