import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Label } from '@/components/ui/label'
import { VM_SIZES, VM_SIZE_IDS, type VmSize, type VmSizeId } from '@/lib/config/vm-sizes'
import { cn } from '@/lib/utils'

function isValidVmSizeId(value: string): value is VmSizeId {
  return (VM_SIZE_IDS as readonly string[]).includes(value)
}

interface VmSizeSelectorProps {
  value?: VmSizeId
  onValueChange: (value: VmSizeId) => void
  disabled?: boolean
}

/**
 * VM Size selector component with visual cards
 *
 * Displays 4 size options (S/M/L/XL) as clickable cards with specs.
 * Uses RadioGroup for proper accessibility and keyboard navigation.
 *
 * @see Story 2.5 AC #1 - Size cards displayed
 * @see Story 2.5 AC #2 - Selection behavior
 * @see Story 2.5 AC #3 - Keyboard accessibility
 * @see Story 2.5 AC #6 - Responsive layout
 */
export function VmSizeSelector({
  value,
  onValueChange,
  disabled = false,
}: VmSizeSelectorProps) {
  return (
    <RadioGroup
      value={value}
      onValueChange={(val) => {
        if (isValidVmSizeId(val)) {
          onValueChange(val)
        }
      }}
      className="grid grid-cols-2 md:grid-cols-4 gap-4"
      disabled={disabled}
      aria-label="Select VM size"
    >
      {VM_SIZES.map((size) => (
        <VmSizeCard
          key={size.id}
          size={size}
          isSelected={value === size.id}
          disabled={disabled}
        />
      ))}
    </RadioGroup>
  )
}

interface VmSizeCardProps {
  size: VmSize
  isSelected: boolean
  disabled?: boolean
}

/**
 * Individual size card with specs display
 *
 * Shows: Size label, vCPU, RAM, Disk, Monthly estimate
 * Highlights when selected with primary ring border.
 */
function VmSizeCard({ size, isSelected, disabled }: VmSizeCardProps) {
  return (
    <Label
      htmlFor={`size-${size.id}`}
      className={cn(
        'flex flex-col items-center justify-center p-4 rounded-lg border-2 cursor-pointer',
        'hover:bg-muted/50 transition-colors min-h-[140px]',
        isSelected
          ? 'border-primary ring-2 ring-primary ring-offset-2 bg-primary/5'
          : 'border-muted',
        disabled && 'cursor-not-allowed opacity-50'
      )}
    >
      <RadioGroupItem
        value={size.id}
        id={`size-${size.id}`}
        className="sr-only"
        aria-label={`${size.label} - ${size.vCpu} vCPU, ${size.ramGb} GB RAM, ${size.diskGb} GB disk`}
      />
      <span className="text-2xl font-bold">{size.id}</span>
      <span className="text-sm text-muted-foreground">{size.vCpu} vCPU</span>
      <span className="text-sm text-muted-foreground">{size.ramGb} GB RAM</span>
      <span className="text-sm text-muted-foreground">{size.diskGb} GB</span>
      <span className="text-xs text-muted-foreground mt-2">
        ~€{size.monthlyEstimateEur}/mo
      </span>
    </Label>
  )
}
