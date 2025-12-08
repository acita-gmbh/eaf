import { CheckCircle, Circle, Clock, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { VmProvisioningStage } from '../../api/vm-requests'
import { format } from 'date-fns'

interface ProvisioningProgressProps {
  stage: VmProvisioningStage
  updatedAt: string
  startedAt?: string
  /** Map of stage name to ISO timestamp when that stage completed */
  stageTimestamps?: Record<string, string>
  /** Estimated seconds remaining until provisioning completes */
  estimatedRemainingSeconds?: number | null
}

const STAGES: { id: VmProvisioningStage; label: string }[] = [
  { id: 'CREATED', label: 'Request Created' },
  { id: 'CLONING', label: 'Cloning VM' },
  { id: 'CONFIGURING', label: 'Configuring Hardware' },
  { id: 'POWERING_ON', label: 'Powering On' },
  { id: 'WAITING_FOR_NETWORK', label: 'Waiting for Network' },
  { id: 'READY', label: 'Ready' },
]

const TIMEOUT_WARNING_MS = 10 * 60 * 1000 // 10 minutes
const ELAPSED_UPDATE_INTERVAL_MS = 10_000 // Update elapsed time every 10 seconds

/**
 * Formats ETA in a human-readable format.
 * @param seconds - Remaining seconds
 * @returns Formatted string like "~45s" or "~2m 15s"
 */
function formatEta(seconds: number): string {
  if (seconds <= 0) return 'Almost done'
  if (seconds < 60) return `~${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  if (remainingSeconds === 0) return `~${minutes}m`
  return `~${minutes}m ${remainingSeconds}s`
}

export function ProvisioningProgress({
  stage,
  updatedAt,
  startedAt,
  stageTimestamps = {},
  estimatedRemainingSeconds,
}: Readonly<ProvisioningProgressProps>) {
  const currentStageIndex = STAGES.findIndex((s) => s.id === stage)

  // Track elapsed time in state to avoid impure Date.now() during render
  // Initialize with a function to compute initial value (avoids effect-based setState)
  const [isTakingLong, setIsTakingLong] = useState(() => {
    if (!startedAt) return false
    const elapsedMs = Date.now() - new Date(startedAt).getTime()
    return elapsedMs > TIMEOUT_WARNING_MS
  })

  useEffect(() => {
    if (!startedAt) return

    // Update periodically to detect timeout
    const interval = setInterval(() => {
      const elapsedMs = Date.now() - new Date(startedAt).getTime()
      setIsTakingLong(elapsedMs > TIMEOUT_WARNING_MS)
    }, ELAPSED_UPDATE_INTERVAL_MS)

    return () => clearInterval(interval)
  }, [startedAt])

  /**
   * Gets the timestamp to display for a given stage.
   * Uses per-stage timestamps when available, falls back to updatedAt.
   */
  const getStageTimestamp = (stageId: VmProvisioningStage, isCurrent: boolean): string | null => {
    // Check if we have a specific timestamp for this stage
    const stageTs = stageTimestamps[stageId]
    if (stageTs) {
      return stageTs
    }

    // For current stage, use updatedAt as fallback
    if (isCurrent) {
      return updatedAt
    }

    return null
  }

  return (
    <div className="rounded-lg border p-4 bg-muted/30">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-medium">Provisioning Progress</h3>
        {estimatedRemainingSeconds != null && estimatedRemainingSeconds > 0 && (
          <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
            <Clock className="h-4 w-4" />
            <span>ETA: {formatEta(estimatedRemainingSeconds)}</span>
          </div>
        )}
      </div>

      {isTakingLong && (
        <div className="mb-4 p-3 bg-yellow-50 border border-yellow-200 rounded-md text-yellow-800 text-sm">
          ⚠️ Provisioning is taking longer than usual...
        </div>
      )}

      <div className="space-y-4">
        {STAGES.map((s, index) => {
          const isCompleted = index < currentStageIndex
          const isCurrent = index === currentStageIndex
          const isPending = index > currentStageIndex

          const timestamp = getStageTimestamp(s.id, isCurrent)

          return (
            <div key={s.id} className="flex items-center gap-3">
              {isCompleted && <CheckCircle className="h-5 w-5 text-green-500" />}
              {isCurrent && <Loader2 className="h-5 w-5 text-blue-500 animate-spin" />}
              {isPending && <Circle className="h-5 w-5 text-muted-foreground" />}

              <div className="flex-1">
                <span className={isCurrent ? 'font-medium' : 'text-muted-foreground'}>
                  {s.label}
                </span>
              </div>

              {(isCompleted || isCurrent) && timestamp && (
                <span className="text-xs text-muted-foreground">
                  {format(new Date(timestamp), 'HH:mm:ss')}
                </span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
