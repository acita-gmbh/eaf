import { Check, Loader2 } from 'lucide-react'
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogCancel,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'

/**
 * Props for ApproveConfirmDialog.
 */
interface ApproveConfirmDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Called when dialog should close */
  onOpenChange: (open: boolean) => void
  /** Called when user confirms approval */
  onConfirm: () => void
  /** Whether the approval is in progress */
  isPending: boolean
  /** VM name to show in dialog */
  vmName: string
  /** Requester name for confirmation context */
  requesterName: string
}

/**
 * Confirmation dialog for approving a VM request.
 *
 * Story 2.11: Approve/Reject Actions
 * AC 1: Approve button click triggers confirmation
 *
 * Shows request details and requires explicit confirmation
 * before dispatching the approve command.
 */
export function ApproveConfirmDialog({
  open,
  onOpenChange,
  onConfirm,
  isPending,
  vmName,
  requesterName,
}: Readonly<ApproveConfirmDialogProps>) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent data-testid="approve-confirm-dialog">
        <AlertDialogHeader>
          <AlertDialogTitle>Approve VM Request</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to approve the request for{' '}
            <span className="font-semibold text-foreground">{vmName}</span> from{' '}
            <span className="font-semibold text-foreground">{requesterName}</span>?
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isPending}>Cancel</AlertDialogCancel>
          <Button
            onClick={onConfirm}
            disabled={isPending}
            data-testid="approve-confirm-button"
          >
            {isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Approving...
              </>
            ) : (
              <>
                <Check className="mr-2 h-4 w-4" />
                Approve Request
              </>
            )}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
