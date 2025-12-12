import { z } from 'zod'
import { VM_SIZE_IDS } from '@/lib/config/vm-sizes'

/**
 * VM Name validation schema
 *
 * Rules:
 * - 3-63 characters
 * - Lowercase letters, numbers, and hyphens only
 * - Must start with letter or number
 * - Must end with letter or number
 *
 * Uses superRefine for granular error messages per AC #2
 */
export const vmNameSchema = z
  .string()
  .min(3, 'Minimum 3 characters required')
  .max(63, 'Maximum 63 characters allowed')
  .superRefine((val, ctx) => {
    // Check for uppercase letters
    if (/[A-Z]/.test(val)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Only lowercase letters allowed',
      })
    }
    // Check for invalid characters (spaces, special chars)
    if (/[^a-z0-9-]/.test(val)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Only letters, numbers, and hyphens allowed',
      })
    }
    // Check start character
    if (val.startsWith('-')) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Must start with a letter or number',
      })
    }
    // Check end character
    if (val.endsWith('-')) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Must end with a letter or number',
      })
    }
  })

/**
 * Justification validation schema
 *
 * Rules:
 * - Minimum 10 characters (per AC #5)
 * - Maximum 1000 characters (soft limit with counter)
 */
export const justificationSchema = z
  .string()
  .min(10, 'Minimum 10 characters required')
  .max(1000, 'Maximum 1000 characters allowed')

/**
 * Project ID validation schema
 *
 * Rules:
 * - Required field (cannot be empty)
 */
export const projectIdSchema = z
  .string()
  .min(1, 'Project is required')

/**
 * VM Size validation schema
 *
 * Uses z.enum with literal values from config.
 * Custom error message for user-friendly validation feedback.
 *
 * @see Story 2.5 AC #5 - Form validation requires size selection
 */
export const vmSizeSchema = z.enum(VM_SIZE_IDS, {
  message: 'Please select a VM size',
})

/**
 * Complete VM Request form schema
 *
 * Combines all field validations including size selector (Story 2.5).
 */
export const vmRequestFormSchema = z.object({
  vmName: vmNameSchema,
  projectId: projectIdSchema,
  justification: justificationSchema,
  size: vmSizeSchema,
})

export type VmRequestFormData = z.infer<typeof vmRequestFormSchema>
