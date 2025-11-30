import { z } from 'zod'

/**
 * VM Name validation schema
 *
 * Rules:
 * - 3-63 characters
 * - Lowercase letters, numbers, and hyphens only
 * - Must start with letter or number
 * - Must end with letter or number
 *
 * Uses superRefine for granular German error messages per AC #2
 */
export const vmNameSchema = z
  .string()
  .min(3, 'Mindestens 3 Zeichen erforderlich')
  .max(63, 'Maximal 63 Zeichen erlaubt')
  .superRefine((val, ctx) => {
    // Check for uppercase letters
    if (/[A-Z]/.test(val)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Nur Kleinbuchstaben erlaubt',
      })
    }
    // Check for invalid characters (spaces, special chars)
    if (/[^a-z0-9-]/.test(val)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Nur Buchstaben, Zahlen und Bindestriche erlaubt',
      })
    }
    // Check start character
    if (val.startsWith('-')) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Muss mit Buchstaben oder Zahl beginnen',
      })
    }
    // Check end character
    if (val.endsWith('-')) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Muss mit Buchstaben oder Zahl enden',
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
  .min(10, 'Mindestens 10 Zeichen erforderlich')
  .max(1000, 'Maximal 1000 Zeichen erlaubt')

/**
 * Project ID validation schema
 *
 * Rules:
 * - Required field (cannot be empty)
 */
export const projectIdSchema = z
  .string()
  .min(1, 'Projekt ist erforderlich')

/**
 * Complete VM Request form schema
 *
 * Combines all field validations.
 * Size selector will be added in Story 2.5.
 */
export const vmRequestFormSchema = z.object({
  vmName: vmNameSchema,
  projectId: projectIdSchema,
  justification: justificationSchema,
  // size will be added in Story 2.5
})

export type VmRequestFormData = z.infer<typeof vmRequestFormSchema>
