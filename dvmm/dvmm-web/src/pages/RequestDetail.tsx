import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import {
  ArrowLeft,
  AlertCircle,
  Cpu,
  MemoryStick,
  HardDrive,
  FileText,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { StatusBadge } from '@/components/requests/StatusBadge'
import { Timeline } from '@/components/requests/Timeline'
import { CancelConfirmDialog } from '@/components/requests/CancelConfirmDialog'
import { useRequestDetail } from '@/hooks/useRequestDetail'
import { useCancelRequest } from '@/hooks/useCancelRequest'
import {
  ApiError,
  isNotFoundError,
  isForbiddenError,
  isInvalidStateError,
} from '@/api/vm-requests'
import { formatDateTime } from '@/lib/date-utils'

/**
 * Page displaying detailed VM request information with timeline.
 *
 * Features:
 * - Full request details (name, project, size, justification)
 * - Status badge with current state
 * - Chronological timeline of all events
 * - Cancel action for pending requests
 * - Auto-polling for real-time updates (30s interval)
 * - Back navigation to My Requests
 *
 * Per AC-1: Clicking a request card navigates to this detail view.
 * Per AC-2: Shows full details including specs and justification.
 * Per AC-3: Timeline shows chronological history.
 * Per AC-4: Polling fetches updates every 30 seconds.
 */
export function RequestDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false)

  // Fetch request detail with polling enabled
  const { data, isLoading, isError, error, refetch } = useRequestDetail(id, {
    polling: true,
    pollInterval: 30000, // 30 seconds per AC-4
  })

  const cancelMutation = useCancelRequest()

  const handleCancelConfirm = (reason?: string) => {
    if (!id) return

    cancelMutation.mutate(
      { requestId: id, reason },
      {
        onSuccess: () => {
          setCancelDialogOpen(false)
          toast.success('Request cancelled successfully')
          // Refetch to update timeline immediately (fire-and-forget)
          void refetch()
        },
        onError: (err) => {
          if (err instanceof ApiError) {
            if (err.status === 404 && isNotFoundError(err.body)) {
              toast.error('Request not found', {
                description: 'The request may have been deleted.',
              })
            } else if (err.status === 403 && isForbiddenError(err.body)) {
              toast.error('Not authorized', {
                description: 'You can only cancel your own requests.',
              })
            } else if (err.status === 409 && isInvalidStateError(err.body)) {
              toast.error('Cannot cancel request', {
                description: `Request is ${err.body.currentState.toLowerCase()} and cannot be cancelled.`,
              })
            } else {
              toast.error('Failed to cancel request', {
                description: err.message,
              })
            }
          } else {
            toast.error('Failed to cancel request', {
              description: 'An unexpected error occurred.',
            })
          }
        },
      }
    )
  }

  const handleBack = () => {
    void navigate('/requests')
  }

  // Loading state (AC-5)
  if (isLoading) {
    return (
      <div className="space-y-6" data-testid="request-detail-loading">
        <BackButton onClick={handleBack} />
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
        </div>
      </div>
    )
  }

  // Error state - Not Found (AC-6)
  if (isError && error?.status === 404) {
    return (
      <div className="space-y-6" data-testid="request-detail-not-found">
        <BackButton onClick={handleBack} />
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <FileText className="h-12 w-12 text-muted-foreground mb-4" />
          <h2 className="text-lg font-semibold mb-2">Request Not Found</h2>
          <p className="text-muted-foreground mb-4">
            The request you're looking for doesn't exist or you don't have access to it.
          </p>
          <Button variant="outline" asChild>
            <Link to="/requests">View My Requests</Link>
          </Button>
        </div>
      </div>
    )
  }

  // General error state (AC-6)
  if (isError) {
    return (
      <div className="space-y-6" data-testid="request-detail-error">
        <BackButton onClick={handleBack} />
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <AlertCircle className="h-12 w-12 text-destructive mb-4" />
          <h2 className="text-lg font-semibold mb-2">Error Loading Request</h2>
          <p className="text-muted-foreground mb-4">
            {error?.message || 'Could not load request details.'}
          </p>
          <Button variant="outline" onClick={() => refetch()}>
            Try Again
          </Button>
        </div>
      </div>
    )
  }

  // Data loaded
  if (!data) {
    return null
  }

  const canCancel = data.status === 'PENDING'

  return (
    <div className="space-y-6" data-testid="request-detail-page">
      {/* Header with back button */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-4">
          <Button
            variant="ghost"
            size="icon"
            onClick={handleBack}
            aria-label="Back to My Requests"
          >
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold" data-testid="request-detail-vm-name">
                {data.vmName}
              </h1>
              <StatusBadge status={data.status} />
            </div>
            <p className="text-muted-foreground" data-testid="request-detail-project">
              Project: {data.projectName}
            </p>
          </div>
        </div>

        {canCancel && (
          <Button
            variant="outline"
            onClick={() => setCancelDialogOpen(true)}
            data-testid="cancel-request-button"
          >
            Cancel Request
          </Button>
        )}
      </div>

      {/* Request Details Card (AC-2) */}
      <Card>
        <CardHeader>
          <CardTitle>Request Details</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Size specifications */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-muted">
                <Cpu className="h-5 w-5 text-muted-foreground" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">CPU</p>
                <p className="font-medium" data-testid="request-detail-cpu">
                  {data.size.cpuCores} cores
                </p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-muted">
                <MemoryStick className="h-5 w-5 text-muted-foreground" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Memory</p>
                <p className="font-medium" data-testid="request-detail-memory">
                  {data.size.memoryGb} GB
                </p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-muted">
                <HardDrive className="h-5 w-5 text-muted-foreground" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Disk</p>
                <p className="font-medium" data-testid="request-detail-disk">
                  {data.size.diskGb} GB
                </p>
              </div>
            </div>
          </div>

          {/* Justification */}
          <div>
            <p className="text-sm text-muted-foreground mb-2">Business Justification</p>
            <p
              className="text-sm bg-muted p-3 rounded-lg"
              data-testid="request-detail-justification"
            >
              {data.justification}
            </p>
          </div>

          {/* Metadata */}
          <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted-foreground">
            <span data-testid="request-detail-requester">
              Requested by: {data.requesterName}
            </span>
            <span data-testid="request-detail-created">
              Created: {formatDateTime(data.createdAt)}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Timeline Card (AC-3) */}
      <Card>
        <CardHeader>
          <CardTitle>Timeline</CardTitle>
        </CardHeader>
        <CardContent>
          <Timeline events={data.timeline} />
        </CardContent>
      </Card>

      {/* Cancel Dialog */}
      <CancelConfirmDialog
        open={cancelDialogOpen}
        onOpenChange={setCancelDialogOpen}
        vmName={data.vmName}
        onConfirm={handleCancelConfirm}
        isPending={cancelMutation.isPending}
      />
    </div>
  )
}

interface BackButtonProps {
  onClick: () => void
}

function BackButton({ onClick }: BackButtonProps) {
  return (
    <Button variant="ghost" size="sm" className="gap-2" onClick={onClick}>
      <ArrowLeft className="h-4 w-4" />
      Back to My Requests
    </Button>
  )
}
