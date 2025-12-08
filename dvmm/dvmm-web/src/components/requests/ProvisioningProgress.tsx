import { CheckCircle, Circle, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { VmProvisioningStage } from '../../api/vm-requests'
import { format } from 'date-fns'

interface ProvisioningProgressProps {
  stage: VmProvisioningStage
  updatedAt: string
  startedAt?: string
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

export function ProvisioningProgress({
  stage,
  updatedAt,
  startedAt,
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

  return (
    <div className="rounded-lg border p-4 bg-muted/30">
      <h3 className="font-medium mb-4">Provisioning Progress</h3>

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

              {(isCompleted || isCurrent) && (
                <span className="text-xs text-muted-foreground">
                  {format(new Date(updatedAt), 'HH:mm:ss')}
                </span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
