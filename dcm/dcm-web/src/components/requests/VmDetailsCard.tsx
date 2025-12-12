import {
  Power,
  PowerOff,
  Network,
  Server,
  Monitor,
  Clock,
  Copy,
  CheckCircle,
  RefreshCw,
  Timer,
} from 'lucide-react'
import { useState } from 'react'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type { VmRuntimeDetails } from '@/api/vm-requests'
import { formatDateTime } from '@/lib/date-utils'

/**
 * Formats uptime duration from boot time to now in human-readable format.
 * Returns null if bootTime is not provided.
 *
 * @example formatUptime('2024-01-01T00:00:00Z') // "2d 4h 30m"
 */
function formatUptime(bootTime: string | null): string | null {
  if (!bootTime) return null

  const bootDate = new Date(bootTime)
  const now = new Date()
  const diffMs = now.getTime() - bootDate.getTime()

  if (diffMs < 0) return null // bootTime in the future (shouldn't happen)

  const totalSeconds = Math.floor(diffMs / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)

  const parts: string[] = []
  if (days > 0) parts.push(`${days}d`)
  if (hours > 0) parts.push(`${hours}h`)
  if (minutes > 0 || parts.length === 0) parts.push(`${minutes}m`)

  return parts.join(' ')
}

interface VmDetailsCardProps {
  vmDetails: VmRuntimeDetails
  /** Callback to refresh VM status from vSphere */
  onRefresh?: () => void
  /** Whether refresh is currently in progress */
  isRefreshing?: boolean
}

/**
 * VM Details Card component for displaying provisioned VM information.
 *
 * Story 3-7: Shows VM runtime details including:
 * - Power state (with visual indicator)
 * - IP address (with copy button)
 * - Hostname
 * - Guest OS
 * - SSH connection instructions
 * - Last synced timestamp
 */
export function VmDetailsCard({
  vmDetails,
  onRefresh,
  isRefreshing = false,
}: Readonly<VmDetailsCardProps>) {
  const [copiedIp, setCopiedIp] = useState(false)
  const [copiedSsh, setCopiedSsh] = useState(false)

  const isPoweredOn = vmDetails.powerState === 'POWERED_ON'
  const isPoweredOff = vmDetails.powerState === 'POWERED_OFF'
  const isSuspended = vmDetails.powerState === 'SUSPENDED'

  // Detect Windows OS for connection instructions (AC-3.7.2)
  const isWindowsOs = vmDetails.guestOs?.toLowerCase().includes('windows') ?? false

  // OS-specific connection command
  const connectionCommand = vmDetails.ipAddress
    ? isWindowsOs
      ? `mstsc /v:${vmDetails.ipAddress}`
      : `ssh <username>@${vmDetails.ipAddress}`
    : null

  const connectionLabel = isWindowsOs ? 'Connect via RDP:' : 'Connect via SSH:'

  // Calculate uptime only when VM is powered on and has bootTime (AC-3.7.2)
  const uptime = isPoweredOn ? formatUptime(vmDetails.bootTime) : null

  const copyToClipboard = async (text: string, type: 'ip' | 'ssh') => {
    try {
      await navigator.clipboard.writeText(text)
      if (type === 'ip') {
        setCopiedIp(true)
        setTimeout(() => setCopiedIp(false), 2000)
      } else {
        setCopiedSsh(true)
        setTimeout(() => setCopiedSsh(false), 2000)
      }
    } catch (error) {
      // Clipboard API can fail in insecure contexts or when permissions are denied
      console.error('Failed to copy to clipboard:', error)
    }
  }

  return (
    <Card data-testid="vm-details-card">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <CardTitle className="text-lg">VM Details</CardTitle>
            {onRefresh && (
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                onClick={onRefresh}
                disabled={isRefreshing}
                aria-label="Refresh VM status"
                data-testid="refresh-vm-status-button"
              >
                <RefreshCw
                  className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`}
                />
              </Button>
            )}
          </div>
          <PowerStateBadge
            powerState={vmDetails.powerState}
            isPoweredOn={isPoweredOn}
            isPoweredOff={isPoweredOff}
            isSuspended={isSuspended}
          />
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Network Information */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* IP Address */}
          <div className="flex items-start gap-3">
            <div className="p-2 rounded-lg bg-muted">
              <Network className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm text-muted-foreground">IP Address</p>
              {vmDetails.ipAddress ? (
                <div className="flex items-center gap-2">
                  <code
                    className="font-mono text-sm font-medium"
                    data-testid="vm-ip-address"
                  >
                    {vmDetails.ipAddress}
                  </code>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-6 w-6"
                    onClick={() => void copyToClipboard(vmDetails.ipAddress!, 'ip')}
                    aria-label="Copy IP address"
                  >
                    {copiedIp ? (
                      <CheckCircle className="h-3.5 w-3.5 text-green-500" />
                    ) : (
                      <Copy className="h-3.5 w-3.5" />
                    )}
                  </Button>
                </div>
              ) : (
                <span className="text-sm text-muted-foreground italic">
                  Not detected
                </span>
              )}
            </div>
          </div>

          {/* Hostname */}
          <div className="flex items-start gap-3">
            <div className="p-2 rounded-lg bg-muted">
              <Server className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm text-muted-foreground">Hostname</p>
              {vmDetails.hostname ? (
                <p
                  className="font-medium truncate"
                  data-testid="vm-hostname"
                  title={vmDetails.hostname}
                >
                  {vmDetails.hostname}
                </p>
              ) : (
                <span className="text-sm text-muted-foreground italic">
                  Not detected
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Guest OS */}
        {vmDetails.guestOs && (
          <div className="flex items-start gap-3">
            <div className="p-2 rounded-lg bg-muted">
              <Monitor className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="flex-1">
              <p className="text-sm text-muted-foreground">Operating System</p>
              <p className="font-medium" data-testid="vm-guest-os">
                {vmDetails.guestOs}
              </p>
            </div>
          </div>
        )}

        {/* Uptime (only shown when powered on with bootTime) */}
        {uptime && (
          <div className="flex items-start gap-3">
            <div className="p-2 rounded-lg bg-muted">
              <Timer className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="flex-1">
              <p className="text-sm text-muted-foreground">Uptime</p>
              <p className="font-medium" data-testid="vm-uptime">
                {uptime}
              </p>
            </div>
          </div>
        )}

        {/* Connection Instructions (SSH for Linux, RDP for Windows) */}
        {isPoweredOn && connectionCommand && (
          <div className="mt-4 p-3 rounded-lg bg-muted/50 border">
            <p className="text-sm text-muted-foreground mb-2">
              {connectionLabel}
            </p>
            <div className="flex items-center gap-2">
              <code
                className="flex-1 font-mono text-sm bg-background px-3 py-2 rounded border"
                data-testid="vm-connection-command"
              >
                {connectionCommand}
              </code>
              <Button
                variant="outline"
                size="sm"
                onClick={() => void copyToClipboard(connectionCommand, 'ssh')}
                className="shrink-0"
              >
                {copiedSsh ? (
                  <>
                    <CheckCircle className="h-4 w-4 mr-1 text-green-500" />
                    Copied
                  </>
                ) : (
                  <>
                    <Copy className="h-4 w-4 mr-1" />
                    Copy
                  </>
                )}
              </Button>
            </div>
          </div>
        )}

        {/* Last Synced */}
        {vmDetails.lastSyncedAt && (
          <div className="flex items-center gap-2 text-xs text-muted-foreground pt-2 border-t">
            <Clock className="h-3.5 w-3.5" />
            <span data-testid="vm-last-synced">
              Last updated: {formatDateTime(vmDetails.lastSyncedAt)}
            </span>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

interface PowerStateBadgeProps {
  powerState: string | null
  isPoweredOn: boolean
  isPoweredOff: boolean
  isSuspended: boolean
}

function PowerStateBadge({
  powerState,
  isPoweredOn,
  isPoweredOff,
  isSuspended,
}: Readonly<PowerStateBadgeProps>) {
  if (isPoweredOn) {
    return (
      <Badge
        variant="default"
        className="bg-green-500/15 text-green-700 hover:bg-green-500/25"
        data-testid="vm-power-state"
      >
        <Power className="h-3 w-3 mr-1" />
        Running
      </Badge>
    )
  }

  if (isPoweredOff) {
    return (
      <Badge
        variant="secondary"
        className="bg-gray-500/15 text-gray-700"
        data-testid="vm-power-state"
      >
        <PowerOff className="h-3 w-3 mr-1" />
        Powered Off
      </Badge>
    )
  }

  if (isSuspended) {
    return (
      <Badge
        variant="secondary"
        className="bg-yellow-500/15 text-yellow-700"
        data-testid="vm-power-state"
      >
        <PowerOff className="h-3 w-3 mr-1" />
        Suspended
      </Badge>
    )
  }

  // Unknown state
  return (
    <Badge variant="outline" data-testid="vm-power-state">
      {powerState ?? 'Unknown'}
    </Badge>
  )
}
