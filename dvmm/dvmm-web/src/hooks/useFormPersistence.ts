import { useEffect } from 'react'

/**
 * Hook to warn users when navigating away with unsaved form data
 *
 * Uses the beforeunload event to show a browser confirmation dialog
 * when the user tries to leave the page with dirty form data.
 *
 * @param isDirty - Whether the form has unsaved changes
 *
 * @example
 * ```tsx
 * const { formState: { isDirty } } = useForm()
 * useFormPersistence(isDirty)
 * ```
 */
export function useFormPersistence(isDirty: boolean) {
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isDirty) {
        e.preventDefault()
        // Modern browsers ignore custom messages, but setting returnValue is required
        e.returnValue = 'Änderungen werden nicht gespeichert. Fortfahren?'
      }
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [isDirty])
}
