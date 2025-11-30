/**
 * VM Size configuration for DVMM
 *
 * Configuration-driven approach allows easy replacement with backend data in future.
 * Used by VmSizeSelector component for rendering size cards.
 *
 * @see Story 2.5 AC #7 - Configuration-based sizes
 */

/**
 * Valid VM size IDs for Zod schema validation
 * Single source of truth - VmSizeId type is derived from this
 */
export const VM_SIZE_IDS = ['S', 'M', 'L', 'XL'] as const

export type VmSizeId = (typeof VM_SIZE_IDS)[number]

export interface VmSize {
  readonly id: VmSizeId
  readonly label: string
  readonly vCpu: number
  readonly ramGb: number
  readonly diskGb: number
  readonly monthlyEstimateEur: number
}

/**
 * Available VM sizes with specifications
 *
 * Matches AC #1 table:
 * | Size | vCPU | RAM | Disk | Monthly Estimate |
 * |------|------|-----|------|------------------|
 * | S    | 2    | 4GB | 50GB | ~€25             |
 * | M    | 4    | 8GB | 100GB| ~€50             |
 * | L    | 8    | 16GB| 200GB| ~€100            |
 * | XL   | 16   | 32GB| 500GB| ~€200            |
 */
export const VM_SIZES: readonly VmSize[] = [
  { id: 'S', label: 'Small', vCpu: 2, ramGb: 4, diskGb: 50, monthlyEstimateEur: 25 },
  { id: 'M', label: 'Medium', vCpu: 4, ramGb: 8, diskGb: 100, monthlyEstimateEur: 50 },
  { id: 'L', label: 'Large', vCpu: 8, ramGb: 16, diskGb: 200, monthlyEstimateEur: 100 },
  { id: 'XL', label: 'Extra Large', vCpu: 16, ramGb: 32, diskGb: 500, monthlyEstimateEur: 200 },
] as const

/**
 * Default VM size selection (per AC #2 - "M" pre-selected)
 */
export const DEFAULT_VM_SIZE: VmSizeId = 'M'

/**
 * Lookup function to find a VM size by ID
 */
export function getVmSizeById(id: string): VmSize | undefined {
  return VM_SIZES.find(size => size.id === id)
}
