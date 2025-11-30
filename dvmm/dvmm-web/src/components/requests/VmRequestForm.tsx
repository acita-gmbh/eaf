import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { vmRequestFormSchema, type VmRequestFormData } from '@/lib/validations/vm-request'
import { ProjectSelect } from './ProjectSelect'
import { useFormPersistence } from '@/hooks/useFormPersistence'
import { cn } from '@/lib/utils'

interface VmRequestFormProps {
  onSubmit?: (data: VmRequestFormData) => void
}

export function VmRequestForm({ onSubmit }: VmRequestFormProps) {
  const form = useForm<VmRequestFormData>({
    resolver: zodResolver(vmRequestFormSchema),
    defaultValues: {
      vmName: '',
      projectId: '',
      justification: '',
    },
    mode: 'onChange', // Validate on change for inline errors per AC #2
  })

  const {
    formState: { isDirty },
    watch,
  } = form

  // Warn on navigation when form is dirty (AC #6)
  useFormPersistence(isDirty)

  const justificationValue = watch('justification')
  const justificationLength = justificationValue?.length || 0
  const isJustificationBelowMin = justificationLength < 10

  const handleSubmit = (data: VmRequestFormData) => {
    // Will be implemented in Story 2.6
    console.log('Form submitted:', data)
    onSubmit?.(data)
  }

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(handleSubmit)}
        className="space-y-6 max-w-xl"
        data-testid="vm-request-form"
      >
        {/* VM Name Field - AC #2 */}
        <FormField
          control={form.control}
          name="vmName"
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                VM-Name <span className="text-destructive">*</span>
              </FormLabel>
              <FormControl>
                <Input
                  placeholder="z.B. web-server-01"
                  aria-required="true"
                  {...field}
                />
              </FormControl>
              <FormDescription>
                3-63 Zeichen, Kleinbuchstaben, Zahlen und Bindestriche
              </FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        {/* Project Select Field - AC #3, #4 */}
        <FormField
          control={form.control}
          name="projectId"
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                Projekt <span className="text-destructive">*</span>
              </FormLabel>
              <FormControl>
                <ProjectSelect
                  value={field.value ?? ''}
                  onValueChange={field.onChange}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        {/* Justification Field - AC #5 */}
        <FormField
          control={form.control}
          name="justification"
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                Begründung <span className="text-destructive">*</span>
              </FormLabel>
              <FormControl>
                <Textarea
                  placeholder="Beschreiben Sie den Zweck dieser VM..."
                  aria-required="true"
                  className="min-h-24 resize-y"
                  maxLength={1000}
                  {...field}
                />
              </FormControl>
              <div className="flex justify-between items-center">
                <div className="flex-1">
                  <FormMessage />
                </div>
                <div
                  className={cn(
                    'text-sm text-right',
                    isJustificationBelowMin ? 'text-destructive' : 'text-muted-foreground'
                  )}
                  aria-live="polite"
                >
                  {justificationLength}/10 Zeichen (min)
                </div>
              </div>
            </FormItem>
          )}
        />

        {/* Size Selector placeholder - Story 2.5 */}
        <div className="rounded-lg border border-dashed border-muted-foreground/25 p-4 text-sm text-muted-foreground">
          VM-Größe Auswahl (Story 2.5)
        </div>

        {/* Submit Button placeholder - Story 2.6 */}
        <div className="rounded-lg border border-dashed border-muted-foreground/25 p-4 text-sm text-muted-foreground">
          Absenden Button (Story 2.6)
        </div>
      </form>
    </Form>
  )
}
