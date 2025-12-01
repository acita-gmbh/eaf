import { useState } from 'react'
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
import { type VmRequestSummary } from '@/api/vm-requests'
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
