import { VmwareConfigForm } from '@/components/admin/VmwareConfigForm'

/**
 * Admin settings page.
 *
 * Story 3.1: VMware Connection Configuration
 *
 * Provides admin interface for configuring VMware vCenter connection
 * settings required for VM provisioning.
 */
export function Settings() {
  return (
    <div className="space-y-6" data-testid="settings-page">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-bold">Settings</h1>
        <p className="text-muted-foreground">
          Configure system settings for your tenant
        </p>
      </div>

      {/* VMware Configuration Section */}
      <VmwareConfigForm />
    </div>
  )
}
