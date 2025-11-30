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
        <h1 className="text-2xl font-bold tracking-tight">Neue VM anfordern</h1>
        <p className="text-muted-foreground">
          Füllen Sie das Formular aus, um eine neue virtuelle Maschine anzufordern.
        </p>
      </div>

      <VmRequestForm />
    </div>
  )
}
