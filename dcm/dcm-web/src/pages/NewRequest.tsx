import { AlertTriangle } from 'lucide-react'
import { VmRequestForm } from '@/components/requests'
import { useVmwareConfigExists } from '@/hooks/useVmwareConfig'

/**
 * New VM Request page
 *
 * Displays the VM request form for creating new VM requests.
 * Form is centered with max-width for readability.
 *
 * Story 3.1 AC-3.1.5: Shows warning and disables form when VMware not configured.
 *
 * @see AC #1, #7
 */
export function NewRequest() {
  const { data: configStatus, isLoading: isLoadingConfig } = useVmwareConfigExists()

  // VMware not configured = form disabled (AC-3.1.5)
  const isVmwareConfigured = configStatus?.exists === true
  const isFormDisabled = !isLoadingConfig && !isVmwareConfigured

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Request New VM</h1>
        <p className="text-muted-foreground">
          Fill out the form to request a new virtual machine.
        </p>
      </div>

      {/* AC-3.1.5: Warning when VMware not configured */}
      {isFormDisabled && (
        <div
          className="rounded-lg border border-destructive bg-destructive/10 p-4"
          role="alert"
          aria-live="polite"
          data-testid="vmware-not-configured-warning"
        >
          <div className="flex items-start gap-3">
            <AlertTriangle className="h-5 w-5 text-destructive flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-medium text-destructive">
                VMware not configured
              </h3>
              <p className="text-sm text-destructive/80 mt-1">
                VM provisioning is not available. Please contact your administrator
                to configure VMware vCenter connection.
              </p>
            </div>
          </div>
        </div>
      )}

      <VmRequestForm disabled={isFormDisabled} />
    </div>
  )
}
