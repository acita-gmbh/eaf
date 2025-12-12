import { z } from 'zod'

/**
 * VMware vCenter URL validation schema.
 *
 * Must be HTTPS URL for security.
 * Max 500 characters per backend constraint.
 */
export const vcenterUrlSchema = z
  .string()
  .min(1, 'vCenter URL is required')
  .max(500, 'Maximum 500 characters allowed')
  .refine(
    (val) => val.startsWith('https://'),
    'vCenter URL must start with https://'
  )

/**
 * Username validation schema.
 *
 * Required, max 255 characters per backend constraint.
 */
export const usernameSchema = z
  .string()
  .min(1, 'Username is required')
  .max(255, 'Maximum 255 characters allowed')

/**
 * Password validation schema.
 *
 * Required for initial setup, optional for updates (empty = keep existing).
 * Max 500 characters per backend constraint.
 */
export const passwordSchema = z
  .string()
  .max(500, 'Maximum 500 characters allowed')
  .optional()

/**
 * Required password schema for connection testing.
 */
export const requiredPasswordSchema = z
  .string()
  .min(1, 'Password is required for connection test')
  .max(500, 'Maximum 500 characters allowed')

/**
 * Datacenter name validation schema.
 *
 * Required, max 255 characters per backend constraint.
 */
export const datacenterNameSchema = z
  .string()
  .min(1, 'Datacenter name is required')
  .max(255, 'Maximum 255 characters allowed')

/**
 * Cluster name validation schema.
 *
 * Required, max 255 characters per backend constraint.
 */
export const clusterNameSchema = z
  .string()
  .min(1, 'Cluster name is required')
  .max(255, 'Maximum 255 characters allowed')

/**
 * Datastore name validation schema.
 *
 * Required, max 255 characters per backend constraint.
 */
export const datastoreNameSchema = z
  .string()
  .min(1, 'Datastore name is required')
  .max(255, 'Maximum 255 characters allowed')

/**
 * Network name validation schema.
 *
 * Required, max 255 characters per backend constraint.
 */
export const networkNameSchema = z
  .string()
  .min(1, 'Network name is required')
  .max(255, 'Maximum 255 characters allowed')

/**
 * Template name validation schema.
 *
 * Optional, max 255 characters per backend constraint.
 * Defaults to "ubuntu-22.04-template" on backend if not provided.
 */
export const templateNameSchema = z
  .string()
  .max(255, 'Maximum 255 characters allowed')
  .optional()

/**
 * Folder path validation schema.
 *
 * Optional, max 500 characters per backend constraint.
 */
export const folderPathSchema = z
  .string()
  .max(500, 'Maximum 500 characters allowed')
  .optional()

/**
 * Complete VMware configuration form schema.
 *
 * Story 3.1 AC-3.1.1: Form fields for vCenter settings
 */
export const vmwareConfigFormSchema = z.object({
  vcenterUrl: vcenterUrlSchema,
  username: usernameSchema,
  password: passwordSchema,
  datacenterName: datacenterNameSchema,
  clusterName: clusterNameSchema,
  datastoreName: datastoreNameSchema,
  networkName: networkNameSchema,
  templateName: templateNameSchema,
  folderPath: folderPathSchema,
})

export type VmwareConfigFormData = z.infer<typeof vmwareConfigFormSchema>

/**
 * Connection test form schema.
 *
 * Story 3.1 AC-3.1.2: "Test Connection" button
 *
 * Requires password for testing.
 */
export const connectionTestSchema = z.object({
  vcenterUrl: vcenterUrlSchema,
  username: usernameSchema,
  password: requiredPasswordSchema,
  datacenterName: datacenterNameSchema,
  clusterName: clusterNameSchema,
  datastoreName: datastoreNameSchema,
  networkName: networkNameSchema,
  templateName: templateNameSchema,
})

export type ConnectionTestFormData = z.infer<typeof connectionTestSchema>
