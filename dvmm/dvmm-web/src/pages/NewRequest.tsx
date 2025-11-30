import { VmRequestForm } from '@/components/requests'

/**
 * New VM Request page
 *
 * Displays the VM request form for creating new VM requests.
 * Form is centered with max-width for readability.
 *
 * @see AC #1, #7
 */
export function NewRequest() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Request New VM</h1>
        <p className="text-muted-foreground">
          Fill out the form to request a new virtual machine.
        </p>
      </div>

      <VmRequestForm />
    </div>
  )
}
