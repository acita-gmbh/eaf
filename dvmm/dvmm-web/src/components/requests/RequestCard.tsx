import { useState } from 'react'
import { toast } from 'sonner'
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
  CardAction,
} from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { StatusBadge } from './StatusBadge'
import { CancelConfirmDialog } from './CancelConfirmDialog'
import {
  ApiError,
  isNotFoundError,
  isForbiddenError,
  isInvalidStateError,
  type VmRequestSummary,
} from '@/api/vm-requests'
import { useCancelRequest } from '@/hooks/useCancelRequest'

interface RequestCardProps {
  request: VmRequestSummary
}

/**
 * Formats an ISO date string to German locale format.
 */
function formatDate(isoDate: string): string {
  return new Date(isoDate).toLocaleDateString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Card component displaying a VM request summary.
 *
 * Shows:
 * - VM name and project
 * - Size specifications (CPU, memory, disk)
 * - Status badge with appropriate color
 * - Creation date
 * - Cancel button (only for PENDING requests)
 */
export function RequestCard({ request }: RequestCardProps) {
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false)
  const cancelMutation = useCancelRequest()

  const handleCancelConfirm = (reason?: string) => {
    cancelMutation.mutate(
      { requestId: request.id, reason },
      {
        onSuccess: () => {
          setCancelDialogOpen(false)
          toast.success('Request cancelled successfully')
        },
        onError: (error) => {
          if (error instanceof ApiError) {
            if (error.status === 404 && isNotFoundError(error.body)) {
              toast.error('Request not found', {
                description: 'The request may have been deleted.',
              })
            } else if (error.status === 403 && isForbiddenError(error.body)) {
              toast.error('Not authorized', {
                description: 'You can only cancel your own requests.',
              })
            } else if (error.status === 409 && isInvalidStateError(error.body)) {
              toast.error('Cannot cancel request', {
                description: `Request is ${error.body.currentState.toLowerCase()} and cannot be cancelled.`,
              })
            } else {
              toast.error('Failed to cancel request', {
                description: error.message,
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

  const canCancel = request.status === 'PENDING'

  return (
    <>
      <Card data-testid={`request-card-${request.id}`}>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <span data-testid="request-vm-name">{request.vmName}</span>
            <StatusBadge status={request.status} />
          </CardTitle>
          <CardDescription data-testid="request-project">
            Project: {request.projectName}
          </CardDescription>
          {canCancel && (
            <CardAction>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCancelDialogOpen(true)}
                data-testid="cancel-request-button"
              >
                Cancel
              </Button>
            </CardAction>
          )}
        </CardHeader>

        <CardContent>
          <div className="grid grid-cols-3 gap-4 text-sm">
            <div>
              <span className="text-muted-foreground">CPU</span>
              <p className="font-medium" data-testid="request-cpu">
                {request.cpuCores} cores
              </p>
            </div>
            <div>
              <span className="text-muted-foreground">Memory</span>
              <p className="font-medium" data-testid="request-memory">
                {request.memoryGb} GB
              </p>
            </div>
            <div>
              <span className="text-muted-foreground">Disk</span>
              <p className="font-medium" data-testid="request-disk">
                {request.diskGb} GB
              </p>
            </div>
          </div>
        </CardContent>

        <CardFooter className="text-muted-foreground text-sm">
          <span data-testid="request-created-at">
            Created {formatDate(request.createdAt)}
          </span>
        </CardFooter>
      </Card>

      <CancelConfirmDialog
        open={cancelDialogOpen}
        onOpenChange={setCancelDialogOpen}
        vmName={request.vmName}
        onConfirm={handleCancelConfirm}
        isPending={cancelMutation.isPending}
      />
    </>
  )
}
