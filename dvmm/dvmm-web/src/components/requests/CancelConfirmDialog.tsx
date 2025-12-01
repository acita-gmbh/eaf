import { useState } from 'react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'

interface CancelConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  vmName: string
  onConfirm: (reason?: string) => void
  isPending: boolean
}

/**
 * Confirmation dialog for cancelling a VM request.
 *
 * Prompts the user to confirm cancellation with an optional reason.
 * Shows loading state while the cancellation is being processed.
 */
export function CancelConfirmDialog({
  open,
  onOpenChange,
  vmName,
  onConfirm,
  isPending,
}: CancelConfirmDialogProps) {
  const [reason, setReason] = useState('')

  const handleConfirm = () => {
    onConfirm(reason.trim() || undefined)
  }

  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen) {
      setReason('')
    }
    onOpenChange(newOpen)
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent data-testid="cancel-confirm-dialog">
        <AlertDialogHeader>
          <AlertDialogTitle>Cancel Request</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to cancel the request for <strong>{vmName}</strong>?
            This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-2">
          <Label htmlFor="cancel-reason">
            Reason <span className="text-muted-foreground">(optional)</span>
          </Label>
          <Textarea
            id="cancel-reason"
            placeholder="Reason for cancellation..."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={isPending}
            data-testid="cancel-reason-input"
          />
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel disabled={isPending} data-testid="cancel-dialog-cancel">
            Go Back
          </AlertDialogCancel>
          <AlertDialogAction
            onClick={handleConfirm}
            disabled={isPending}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            data-testid="cancel-dialog-confirm"
          >
            {isPending ? 'Cancelling...' : 'Cancel Request'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
