import { Badge } from '@/components/ui/badge'
import { type VmRequestStatus } from '@/api/vm-requests'
import { cn } from '@/lib/utils'

/**
 * Status badge color configuration.
 *
 * Maps VM request status to appropriate Tailwind color classes
 * for visual differentiation in the UI.
 */
const statusColors: Record<VmRequestStatus, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800 border-yellow-200',
  APPROVED: 'bg-green-100 text-green-800 border-green-200',
  REJECTED: 'bg-red-100 text-red-800 border-red-200',
  CANCELLED: 'bg-gray-100 text-gray-800 border-gray-200',
  PROVISIONING: 'bg-blue-100 text-blue-800 border-blue-200',
  READY: 'bg-emerald-100 text-emerald-800 border-emerald-200',
  FAILED: 'bg-red-100 text-red-800 border-red-200',
}

/**
 * Display labels for VM request statuses.
 */
const statusLabels: Record<VmRequestStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  PROVISIONING: 'Provisioning',
  READY: 'Ready',
  FAILED: 'Failed',
}

interface StatusBadgeProps {
  status: VmRequestStatus
  className?: string
}

/**
 * Displays a colored badge for VM request status.
 *
 * Each status has a distinct color for quick visual identification:
 * - PENDING: Yellow (awaiting review)
 * - APPROVED: Green (accepted)
 * - REJECTED: Red (denied)
 * - CANCELLED: Gray (user cancelled)
 * - PROVISIONING: Blue (in progress)
 * - READY: Emerald (complete)
 * - FAILED: Red (error)
 */
export function StatusBadge({ status, className }: Readonly<StatusBadgeProps>) {
  // Safe: status is typed VmRequestStatus enum, not user input
  // eslint-disable-next-line security/detect-object-injection
  const colors = statusColors[status]
  // eslint-disable-next-line security/detect-object-injection
  const label = statusLabels[status]

  return (
    <Badge
      variant="outline"
      className={cn(colors, className)}
      data-testid={`status-badge-${status.toLowerCase()}`}
    >
      {label}
    </Badge>
  )
}
