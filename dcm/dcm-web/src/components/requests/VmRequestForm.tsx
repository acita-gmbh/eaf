import { useForm, useWatch } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { Loader2 } from 'lucide-react'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { vmRequestFormSchema, type VmRequestFormData } from '@/lib/validations/vm-request'
import { ProjectSelect } from './ProjectSelect'
import { VmSizeSelector } from './VmSizeSelector'
import { VmSizeQuotaInfo } from './VmSizeQuotaInfo'
import { useFormPersistence } from '@/hooks/useFormPersistence'
import { useCreateVmRequest } from '@/hooks/useCreateVmRequest'
import { ApiError, isValidationError, isQuotaExceededError } from '@/api/vm-requests'
import { cn } from '@/lib/utils'
import { DEFAULT_VM_SIZE } from '@/lib/config/vm-sizes'
import { MOCK_PROJECTS } from '@/lib/mock-data/projects'

interface VmRequestFormProps {
  onSubmit?: (data: VmRequestFormData) => void
  /**
   * Disable the form submission.
   * Used when VMware is not configured (AC-3.1.5).
   */
  disabled?: boolean
}

export function VmRequestForm({ onSubmit, disabled = false }: Readonly<VmRequestFormProps>) {
  const navigate = useNavigate()
  const mutation = useCreateVmRequest()

  const form = useForm<VmRequestFormData>({
    resolver: zodResolver(vmRequestFormSchema),
    defaultValues: {
      vmName: '',
      projectId: '',
      justification: '',
      size: DEFAULT_VM_SIZE,
    },
    mode: 'onChange', // Validate on change for inline errors per AC #2
  })

  const {
    formState: { isDirty, isValid },
    control,
    setError,
  } = form

  // Warn on navigation when form is dirty (AC #6)
  useFormPersistence(isDirty)

  // Use useWatch instead of watch() for React Compiler compatibility
  const justificationValue = useWatch({ control, name: 'justification' })
  const justificationLength = justificationValue.length
  const isJustificationBelowMin = justificationLength < 10

  // Watch project selection for quota display
  const selectedProjectId = useWatch({ control, name: 'projectId' })
  const selectedProject = MOCK_PROJECTS.find((p) => p.id === selectedProjectId)

  const handleSubmit = (data: VmRequestFormData) => {
    mutation.mutate(data, {
      onSuccess: (result) => {
        toast.success('Request submitted!', {
          description: `VM "${data.vmName}" has been submitted for approval.`,
        })
        onSubmit?.(data)
        void navigate(`/requests/${result.id}`)
      },
      onError: (error) => {
        if (error instanceof ApiError) {
          if (error.status === 400 && isValidationError(error.body)) {
            // Map backend validation errors to form fields
            error.body.errors.forEach((err) => {
              const fieldName = err.field as keyof VmRequestFormData
              if (fieldName in form.getValues()) {
                setError(fieldName, { message: err.message })
              }
            })
            toast.error('Validation error', {
              description: 'Please correct the highlighted fields.',
            })
          } else if (error.status === 409 && isQuotaExceededError(error.body)) {
            // Quota exceeded - show as root error
            setError('root', {
              message: `Project quota exceeded. Available: ${error.body.available} VMs`,
            })
            toast.error('Quota exceeded', {
              description: error.body.message,
            })
          } else if (error.status === 401) {
            toast.error('Not authenticated', {
              description: 'Please sign in again.',
            })
          } else {
            toast.error('Request failed', {
              description: `Status: ${error.status}`,
            })
          }
        } else {
          toast.error('Connection error', {
            description: 'Please try again.',
          })
        }
      },
    })
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
                VM Name <span className="text-destructive">*</span>
              </FormLabel>
              <FormControl>
                <Input
                  placeholder="e.g. web-server-01"
                  aria-required="true"
                  {...field}
                />
              </FormControl>
              <FormDescription>
                3-63 characters, lowercase letters, numbers, and hyphens
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
                Project <span className="text-destructive">*</span>
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
                Justification <span className="text-destructive">*</span>
              </FormLabel>
              <FormControl>
                <Textarea
                  placeholder="Describe the purpose of this VM..."
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
                  {justificationLength}/10 characters (min)
                </div>
              </div>
            </FormItem>
          )}
        />

        {/* Size Selector - Story 2.5 */}
        <FormField
          control={form.control}
          name="size"
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                VM Size <span className="text-destructive">*</span>
              </FormLabel>
              <FormControl>
                <VmSizeSelector
                  value={field.value}
                  onValueChange={field.onChange}
                />
              </FormControl>
              <VmSizeQuotaInfo projectQuota={selectedProject?.quota} />
              <FormMessage />
            </FormItem>
          )}
        />

        {/* Root-level errors (e.g., quota exceeded) */}
        {form.formState.errors.root && (
          <div
            className="rounded-lg border border-destructive bg-destructive/10 p-4 text-sm text-destructive"
            role="alert"
            aria-live="polite"
          >
            {form.formState.errors.root.message}
          </div>
        )}

        {/* Submit Button - AC #1, AC-3.1.5 (disabled when VMware not configured) */}
        <Button
          type="submit"
          disabled={disabled || !isValid || mutation.isPending}
          className="w-full"
          data-testid="submit-button"
          aria-label={
            disabled
              ? 'VMware not configured - contact administrator'
              : mutation.isPending
                ? 'Submitting request...'
                : 'Submit request'
          }
          title={disabled ? 'VMware not configured - contact administrator' : undefined}
        >
          {mutation.isPending ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" data-testid="submit-loading" />
              Submitting...
            </>
          ) : (
            'Submit Request'
          )}
        </Button>
      </form>
    </Form>
  )
}
