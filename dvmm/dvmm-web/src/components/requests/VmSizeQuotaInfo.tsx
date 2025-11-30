import { Progress } from '@/components/ui/progress'
import { AlertTriangle } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { MockProject } from '@/lib/mock-data/projects'

interface VmSizeQuotaInfoProps {
  projectQuota: MockProject['quota'] | undefined
}

/**
 * Quota display component for VM size selection
 *
 * Shows remaining VM quota for the selected project.
 * Displays warning styling when quota is ≥80% used.
 *
 * @see Story 2.5 AC #4 - Quota check display (display-only)
 */
export function VmSizeQuotaInfo({ projectQuota }: VmSizeQuotaInfoProps) {
  if (!projectQuota) return null

  // Defensive: ensure remaining is never negative (handles used > total edge case)
  const remaining = Math.max(0, projectQuota.total - projectQuota.used)
  // Defensive: clamp percentage to 0-100 range
  const usagePercent =
    projectQuota.total > 0
      ? Math.min(100, Math.max(0, Math.round((projectQuota.used / projectQuota.total) * 100)))
      : 100 // Zero total means fully exhausted
  const isWarning = usagePercent >= 80

  return (
    <div
      className={cn(
        'rounded-lg border p-4 mt-4',
        isWarning
          ? 'border-amber-500 bg-amber-50 dark:bg-amber-950/20'
          : 'border-muted'
      )}
      role="region"
      aria-live="polite"
      aria-label="Quota status"
    >
      <div className="flex items-center justify-between text-sm mb-2">
        <span>
          Available: {remaining} of {projectQuota.total} VMs
        </span>
        {isWarning && (
          <AlertTriangle className="w-4 h-4 text-amber-600 dark:text-amber-500" />
        )}
      </div>
      <Progress
        value={usagePercent}
        className={cn('h-2', isWarning && '[&>div]:bg-amber-500')}
      />
    </div>
  )
}
