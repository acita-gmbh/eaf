import { useState } from 'react'
import { X, Loader2 } from 'lucide-react'
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
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

/**
 * Props for RejectFormDialog.
 */
interface RejectFormDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Called when dialog should close */
  onOpenChange: (open: boolean) => void
  /** Called when user confirms rejection with reason */
  onConfirm: (reason: string) => void
  /** Whether the rejection is in progress */
  isPending: boolean
  /** VM name to show in dialog */
  vmName: string
  /** Requester name for context */
  requesterName: string
}

const MIN_REASON_LENGTH = 10
const MAX_REASON_LENGTH = 500

/**
 * Form dialog for rejecting a VM request with a mandatory reason.
 *
 * Story 2.11: Approve/Reject Actions
 * AC 2: Reject requires reason (10-500 characters)
 * AC 3: Dialog modal behavior
 *
 * Validates reason length before allowing submission.
 */
export function RejectFormDialog({
  open,
  onOpenChange,
  onConfirm,
  isPending,
  vmName,
  requesterName,
}: Readonly<RejectFormDialogProps>) {
  const [reason, setReason] = useState('')

  const isValidReason = reason.length >= MIN_REASON_LENGTH && reason.length <= MAX_REASON_LENGTH
  const remainingChars = MAX_REASON_LENGTH - reason.length

  const handleSubmit = () => {
    if (isValidReason) {
      onConfirm(reason)
    }
  }

  // Reset form when dialog closes
  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen) {
      setReason('')
    }
    onOpenChange(newOpen)
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent data-testid="reject-form-dialog">
        <AlertDialogHeader>
          <AlertDialogTitle>Reject VM Request</AlertDialogTitle>
          <AlertDialogDescription>
            You are about to reject the request for{' '}
            <span className="font-semibold text-foreground">{vmName}</span> from{' '}
            <span className="font-semibold text-foreground">{requesterName}</span>.
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-2">
          <Label htmlFor="rejection-reason">
            Rejection Reason <span className="text-destructive">*</span>
          </Label>
          <Textarea
            id="rejection-reason"
            placeholder="Please explain why this request is being rejected (10-500 characters)..."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={isPending}
            rows={4}
            maxLength={MAX_REASON_LENGTH}
            data-testid="rejection-reason-input"
            aria-describedby="reason-hint"
          />
          <div
            id="reason-hint"
            className="flex justify-between text-xs text-muted-foreground"
          >
            <span>
              {reason.length < MIN_REASON_LENGTH && (
                <span className="text-amber-600">
                  Minimum {MIN_REASON_LENGTH} characters required
                </span>
              )}
            </span>
            <span className={remainingChars < 50 ? 'text-amber-600' : ''}>
              {remainingChars} characters remaining
            </span>
          </div>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel disabled={isPending}>Cancel</AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={handleSubmit}
            disabled={isPending || !isValidReason}
            data-testid="reject-confirm-button"
          >
            {isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Rejecting...
              </>
            ) : (
              <>
                <X className="mr-2 h-4 w-4" />
                Reject Request
              </>
            )}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
