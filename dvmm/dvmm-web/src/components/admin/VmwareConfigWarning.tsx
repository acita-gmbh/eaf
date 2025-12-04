import { Link } from 'react-router-dom'
import { AlertTriangle, ExternalLink } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useVmwareConfigExists } from '@/hooks/useVmwareConfig'
import { useIsAdmin } from '@/hooks/useIsAdmin'

/**
 * Warning banner displayed when VMware vCenter is not configured.
 *
 * Story 3.1 AC-3.1.5: "VMware not configured" warning
 *
 * Only shown to admin users when:
 * - VMware configuration does not exist for the tenant
 * - Or configuration exists but has never been verified
 *
 * Shows a link to the settings page to configure vCenter.
 */
export function VmwareConfigWarning() {
  const isAdmin = useIsAdmin()
  const { data, isLoading, isError } = useVmwareConfigExists()

  // Don't show to non-admins
  if (!isAdmin) {
    return null
  }

  // Don't show while loading or on error (fail silently)
  if (isLoading || isError) {
    return null
  }

  // Don't show if config exists and has been verified
  if (data?.exists && data?.verifiedAt) {
    return null
  }

  // Show warning if config doesn't exist
  if (!data?.exists) {
    return (
      <div
        className="rounded-lg border border-yellow-500 bg-yellow-50 p-4 dark:border-yellow-600 dark:bg-yellow-950/20"
        role="alert"
        aria-live="polite"
        data-testid="vmware-config-warning"
      >
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <AlertTriangle className="h-5 w-5 text-yellow-600 dark:text-yellow-500 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-medium text-yellow-800 dark:text-yellow-200">
                VMware vCenter Not Configured
              </h3>
              <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
                VM provisioning will not work until vCenter connection is configured.
              </p>
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            asChild
            className="border-yellow-600 text-yellow-700 hover:bg-yellow-100 dark:border-yellow-500 dark:text-yellow-300 dark:hover:bg-yellow-900/30"
          >
            <Link to="/admin/settings" className="gap-2">
              Configure vCenter
              <ExternalLink className="h-4 w-4" />
            </Link>
          </Button>
        </div>
      </div>
    )
  }

  // Config exists but never verified - show milder warning
  if (data?.exists && !data?.verifiedAt) {
    return (
      <div
        className="rounded-lg border border-yellow-500/50 bg-yellow-50/50 p-4 dark:border-yellow-600/50 dark:bg-yellow-950/10"
        role="alert"
        aria-live="polite"
        data-testid="vmware-config-unverified-warning"
      >
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <AlertTriangle className="h-5 w-5 text-yellow-500 dark:text-yellow-600 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-medium text-yellow-700 dark:text-yellow-300">
                VMware Connection Not Verified
              </h3>
              <p className="text-sm text-yellow-600 dark:text-yellow-400 mt-1">
                Test the vCenter connection to ensure VM provisioning works correctly.
              </p>
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            asChild
            className="border-yellow-500 text-yellow-600 hover:bg-yellow-100 dark:border-yellow-600 dark:text-yellow-400 dark:hover:bg-yellow-900/30"
          >
            <Link to="/admin/settings" className="gap-2">
              Verify Connection
              <ExternalLink className="h-4 w-4" />
            </Link>
          </Button>
        </div>
      </div>
    )
  }

  return null
}
