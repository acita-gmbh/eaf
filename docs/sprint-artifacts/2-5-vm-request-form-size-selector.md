# Story 2.5: VM Request Form - Size Selector

Status: ready-for-dev

## Story

As an **end user**,
I want to select a VM size with clear specifications,
So that I can choose the right resources for my needs.

## Requirements Context Summary

- **Epic/AC source:** Story 2.5 in `docs/epics.md` - VM Request Form - Size Selector
- **FRs Satisfied:** FR18 (VM Size Selection), FR83 (Quota Visibility)
- **Architecture constraint:** React Hook Form + Zod validation, shadcn-admin-kit patterns, Radio Group + Cards
- **Prerequisites:** Story 2.4 (VM Request Form - Basic Fields) - DONE
- **UX Reference:** `docs/ux-design-specification.md` Section 6.4 - VMSizeSelector component
- **Existing Code:** VmRequestForm with placeholder in `dvmm/dvmm-web/src/components/requests/VmRequestForm.tsx`

## Pre-Flight Setup Checklist

Before starting implementation, verify these are complete:

- [x] **Story 2.4 completed:** VmRequestForm exists with Size Selector placeholder
- [x] **lucide-react installed:** `npm list lucide-react` shows version (required for AlertTriangle icon)
- [ ] **shadcn radio-group component:** `npm list @radix-ui/react-radio-group` shows installed (required for RadioGroup)
- [ ] **Folder structure exists:** `src/components/requests/`, `src/lib/validations/`, `src/lib/mock-data/`
- [ ] **New folder to create:** `src/lib/config/` (for vm-sizes.ts)

If shadcn radio-group is missing, complete Task 1 first.

## Acceptance Criteria

1. **Size cards displayed**
   - Given I am on the VM request form
   - When I view the size selector section
   - Then I see 4 visual cards arranged horizontally (responsive: 2x2 on mobile):

   | Size | vCPU | RAM | Disk | Monthly Estimate |
   |------|------|-----|------|------------------|
   | S | 2 | 4 GB | 50 GB | ~€25 |
   | M | 4 | 8 GB | 100 GB | ~€50 |
   | L | 8 | 16 GB | 200 GB | ~€100 |
   | XL | 16 | 32 GB | 500 GB | ~€200 |

   - And each card shows: Size label (S/M/L/XL), vCPU count, RAM, Disk, Monthly cost estimate
   - And cards have consistent styling with the design system (teal primary color)

2. **Selection behavior**
   - Given I view the size selector
   - When the form loads
   - Then "M" (Medium) is pre-selected as the default
   - And the selected card has a highlighted border (teal ring) and subtle background

   - Given I click on a different size card
   - When I click "L" (Large)
   - Then the "L" card becomes selected with highlighted styling
   - And the previous selection ("M") loses its highlight
   - And only one size can be selected at a time (radio behavior)

3. **Keyboard accessibility**
   - Given I have focused the size selector
   - When I use arrow keys (Left/Right or Up/Down)
   - Then I can navigate between size options
   - And pressing Enter or Space selects the focused option
   - And focus ring is visible on the focused card

4. **Quota check display (display-only)**
   - Given I have selected a project (from AC #3 of Story 2.4)
   - When I select a VM size
   - Then I see a quota indicator below the size selector showing:
     - "Available: X of Y VMs" (reuses existing ProjectQuotaDisplay pattern)
     - Progress bar visualization
   - And if project quota is nearly exhausted (>80%), show warning styling
   - And the size card is NOT disabled (quota enforced on submit, not selection)

   **Note:** For MVP, quota display shows VM count (matches existing `MOCK_PROJECTS` structure). vCPU-based quotas will be implemented in Epic 4. Quota enforcement happens in Story 2.6 on submit. This story only displays the existing project quota.

5. **Form integration**
   - Given I have selected a size
   - When I view the form state
   - Then the size value is stored in the form (e.g., "S", "M", "L", "XL")
   - And the form validation requires a size to be selected
   - And the size field is integrated with React Hook Form

   - Given I have not selected a size (validation disabled default)
   - When I try to submit the form
   - Then I see validation error: "Please select a VM size"

6. **Responsive layout**
   - Given I view the size selector on desktop (≥768px)
   - Then the 4 cards are displayed in a single row (flex with gap)

   - Given I view the size selector on mobile (<768px)
   - Then the cards wrap to a 2x2 grid
   - And cards remain touch-friendly (min 44px tap target)

7. **Configuration-based sizes**
   - Given the VM sizes are defined
   - When the component loads
   - Then sizes are loaded from a configuration constant (not hardcoded in JSX)
   - And the configuration includes: id, label, vCpu, ramGb, diskGb, monthlyEstimateEur
   - And this configuration can be easily replaced with backend data in future

## Test Plan

### Unit Tests

**VmSizeSelector component:**
- Renders all 4 size cards (S, M, L, XL)
- Shows correct specs for each size (vCPU, RAM, disk, cost)
- Pre-selects "M" by default
- Highlights selected card with ring styling
- Calls onValueChange when selection changes
- Handles keyboard navigation (arrow keys)
- Renders in single row on desktop
- Renders in 2x2 grid on mobile (mocked viewport)

**VmSizeCard component (if extracted):**
- Renders size label, vCPU, RAM, disk, cost
- Shows selected state with ring border
- Shows hover state on interaction
- Applies correct aria attributes (aria-checked)

**Quota indicator integration:**
- Displays quota when project is selected
- Shows warning styling when project quota >80% used
- Does NOT disable size selection (display-only warning)
- Reuses existing ProjectQuotaDisplay styling pattern
- Shows AlertTriangle icon when quota warning active

**Zod schema (sizeSchema):**
- Rejects undefined value
- Rejects empty string
- Accepts valid sizes: "S", "M", "L", "XL"
- Rejects invalid strings (e.g., "XXL", "small")

### Integration Tests

- Size selector integrates with VmRequestForm
- Form validation fails without size selection
- Form state includes selected size value
- Quota display appears when project is selected
- Quota warning styling appears when project quota >80% used

### Accessibility Tests

- Size selector has role="radiogroup"
- Each card has role="radio" with aria-checked
- Focus is visible on keyboard navigation
- Size selector has accessible label
- Quota warning is announced to screen readers (aria-live="polite")

## Structure Alignment / Previous Learnings

### Learnings from Story 2-4-vm-request-form-basic-fields (Status: done)

- **Form validation pattern:**
  - React Hook Form with Zod resolver established
  - `mode: 'onChange'` for inline validation
  - FormField wrapper pattern with FormControl, FormLabel, FormMessage

- **Component organization:**
  - Separate components per form section (ProjectSelect, NoProjectHelpDialog)
  - Index.ts barrel exports for clean imports
  - Test files co-located with components

- **Testing patterns:**
  - Vitest + @testing-library/react
  - Mock data constants in `src/lib/mock-data/`
  - Test IDs for important elements (data-testid)

- **Styling patterns:**
  - Tailwind 4 with CSS variables
  - cn() utility for conditional classes
  - Primary color: teal-700 (#0f766e)
  - Warning color: amber-500 for quota warnings

- **Accessibility patterns:**
  - aria-required for required fields
  - aria-live="polite" for dynamic content
  - FormDescription for help text

[Source: docs/sprint-artifacts/2-4-vm-request-form-basic-fields.md]

### UX Design Reference

**VMSizeSelector visual spec (from UX Design):**
```
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│  S  │ │  M  │ │  L  │ │ XL  │
│2CPU │ │4CPU │ │8CPU │ │16CPU│
│4GB  │ │8GB  │ │16GB │ │32GB │
└─────┘ └─────┘ └─────┘ └─────┘
  □       ■       □       □     (selected state)
```

**QuotaIndicator visual spec:**
```
Your Quota                      75% used
[████████████████░░░░░░]
This request uses 8 of 32 remaining vCPUs
```

[Source: docs/ux-design-specification.md#Section-6.4]

### Architecture Patterns

- **Configuration-driven:** VM sizes defined in constant, not hardcoded
- **Radio Group pattern:** shadcn RadioGroup with custom card rendering
- **Form integration:** React Hook Form Controller for custom components

## Tasks / Subtasks

- [ ] **Task 1: Add shadcn radio-group component** (Setup)
  - [ ] Run `npx shadcn@latest add radio-group`
  - [ ] Verify `src/components/ui/radio-group.tsx` created
  - [ ] Verify @radix-ui/react-radio-group in package.json

- [ ] **Task 2: Create VM size configuration** (AC: 7)
  - [ ] Create folder: `mkdir -p src/lib/config`
  - [ ] Create `src/lib/config/vm-sizes.ts`
  - [ ] Define `VmSize` type with id, label, vCpu, ramGb, diskGb, monthlyEstimateEur
  - [ ] Export `VM_SIZES` constant array with S, M, L, XL specs
  - [ ] Export `DEFAULT_VM_SIZE = "M"` constant
  - [ ] Write unit tests for configuration validity

- [ ] **Task 3: Extend Zod schema with size validation** (AC: 5) - *Depends on: Task 2*
  - [ ] Add `vmSizeSchema` to `src/lib/validations/vm-request.ts`
  - [ ] Use z.enum() with VM_SIZES ids: ["S", "M", "L", "XL"]
  - [ ] Add custom error message: "Please select a VM size"
  - [ ] Update `vmRequestFormSchema` to include size field
  - [ ] Update `VmRequestFormData` type
  - [ ] Write unit tests for size validation

- [ ] **Task 4: Create VmSizeSelector component** (AC: 1, 2, 3, 6)
  - [ ] Create `src/components/requests/VmSizeSelector.tsx`
  - [ ] Use shadcn RadioGroup with custom card rendering
  - [ ] Display all 4 sizes in horizontal flex (wrap on mobile)
  - [ ] Show specs: size label, vCPU, RAM, disk, monthly cost
  - [ ] Implement selected state with teal ring border
  - [ ] Add hover state for cards
  - [ ] Ensure keyboard navigation works (RadioGroup handles this)
  - [ ] Accept `value` and `onValueChange` props for React Hook Form
  - [ ] Set defaultValue to "M"
  - [ ] Write unit tests (8+ tests for rendering and behavior)

- [ ] **Task 5: Create VmSizeQuotaInfo component** (AC: 4) - *Depends on: Task 2*
  - [ ] Create `src/components/requests/VmSizeQuotaInfo.tsx`
  - [ ] Accept `projectQuota` prop (reuse `MockProject['quota']` type)
  - [ ] Reuse existing `ProjectQuotaDisplay` pattern from ProjectSelect.tsx
  - [ ] Display: "Available: X of Y VMs" with progress bar
  - [ ] Show warning styling (amber) when quota >80% used
  - [ ] Show AlertTriangle icon when in warning state
  - [ ] Add dark mode support: `bg-amber-50 dark:bg-amber-950/20`
  - [ ] Use aria-live="polite" for accessibility
  - [ ] Write unit tests (5+ tests for quota display and warning states)

- [ ] **Task 6: Integrate VmSizeSelector into VmRequestForm** (AC: 5)
  - [ ] Import VmSizeSelector and VmSizeQuotaInfo into VmRequestForm
  - [ ] Replace placeholder div with FormField for size
  - [ ] Add `size: 'M'` to form defaultValues (DEFAULT_VM_SIZE constant)
  - [ ] Pass selected project's quota to VmSizeQuotaInfo
  - [ ] Update form to include size in submitted data
  - [ ] Write integration tests

- [ ] **Task 7: Update barrel exports** (Cleanup)
  - [ ] Add VmSizeSelector to `src/components/requests/index.ts`
  - [ ] Add VmSizeQuotaInfo to exports
  - [ ] Add vm-sizes config to lib exports if needed

- [ ] **Task 8: Write integration tests** (Test Plan)
  - [ ] Test full form with size selector renders
  - [ ] Test form validation requires size selection
  - [ ] Test quota warning displays when project selected
  - [ ] Test size change updates quota warning

## Dev Notes

### Existing Files to Modify

| File | Changes Required |
|------|------------------|
| `src/components/requests/VmRequestForm.tsx` | Replace size placeholder, add FormField |
| `src/lib/validations/vm-request.ts` | Add vmSizeSchema, update main schema |
| `src/components/requests/index.ts` | Add new component exports |

### New Files to Create

```
dvmm/dvmm-web/src/
├── components/
│   └── requests/
│       ├── VmSizeSelector.tsx        # Main size selector component
│       ├── VmSizeSelector.test.tsx   # Unit tests
│       ├── VmSizeQuotaInfo.tsx       # Quota display component
│       ├── VmSizeQuotaInfo.test.tsx
│       └── index.ts                  # Update barrel exports
├── lib/
│   ├── config/
│   │   ├── vm-sizes.ts              # VM size configuration
│   │   └── vm-sizes.test.ts         # Config validation tests
│   └── validations/
│       └── vm-request.ts            # Update with size schema
└── components/ui/
    └── radio-group.tsx              # shadcn component (auto-generated)
```

### VM Size Configuration

```typescript
// src/lib/config/vm-sizes.ts
export interface VmSize {
  id: 'S' | 'M' | 'L' | 'XL'
  label: string
  vCpu: number
  ramGb: number
  diskGb: number
  monthlyEstimateEur: number
}

export const VM_SIZES: VmSize[] = [
  { id: 'S', label: 'Small', vCpu: 2, ramGb: 4, diskGb: 50, monthlyEstimateEur: 25 },
  { id: 'M', label: 'Medium', vCpu: 4, ramGb: 8, diskGb: 100, monthlyEstimateEur: 50 },
  { id: 'L', label: 'Large', vCpu: 8, ramGb: 16, diskGb: 200, monthlyEstimateEur: 100 },
  { id: 'XL', label: 'Extra Large', vCpu: 16, ramGb: 32, diskGb: 500, monthlyEstimateEur: 200 },
] as const

export const DEFAULT_VM_SIZE: VmSize['id'] = 'M'

export function getVmSizeById(id: string): VmSize | undefined {
  return VM_SIZES.find(size => size.id === id)
}
```

### Zod Schema Extension

```typescript
// Add to src/lib/validations/vm-request.ts
import { VM_SIZES } from '@/lib/config/vm-sizes'

/**
 * VM Size validation schema
 *
 * Uses z.enum with literal values from config
 */
export const vmSizeSchema = z.enum(
  ['S', 'M', 'L', 'XL'] as const,
  { errorMap: () => ({ message: 'Please select a VM size' }) }
)

// Update vmRequestFormSchema
export const vmRequestFormSchema = z.object({
  vmName: vmNameSchema,
  projectId: projectIdSchema,
  justification: justificationSchema,
  size: vmSizeSchema, // Added in Story 2.5
})
```

### VmSizeSelector Component

```tsx
// src/components/requests/VmSizeSelector.tsx
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Label } from '@/components/ui/label'
import { VM_SIZES, type VmSize } from '@/lib/config/vm-sizes'
import { cn } from '@/lib/utils'

interface VmSizeSelectorProps {
  value?: string
  onValueChange: (value: string) => void
  disabled?: boolean
}

export function VmSizeSelector({ value, onValueChange, disabled }: VmSizeSelectorProps) {
  return (
    <RadioGroup
      value={value}
      onValueChange={onValueChange}
      className="grid grid-cols-2 md:grid-cols-4 gap-4"
      disabled={disabled}
      aria-label="Select VM size"
    >
      {VM_SIZES.map((size) => (
        <VmSizeCard
          key={size.id}
          size={size}
          isSelected={value === size.id}
        />
      ))}
    </RadioGroup>
  )
}

interface VmSizeCardProps {
  size: VmSize
  isSelected: boolean
}

function VmSizeCard({ size, isSelected }: VmSizeCardProps) {
  return (
    <Label
      htmlFor={`size-${size.id}`}
      className={cn(
        'flex flex-col items-center justify-center p-4 rounded-lg border-2 cursor-pointer',
        'hover:bg-muted/50 transition-colors',
        isSelected
          ? 'border-primary ring-2 ring-primary ring-offset-2 bg-primary/5'
          : 'border-muted'
      )}
    >
      <RadioGroupItem
        value={size.id}
        id={`size-${size.id}`}
        className="sr-only"
      />
      <span className="text-2xl font-bold">{size.id}</span>
      <span className="text-sm text-muted-foreground">{size.vCpu} vCPU</span>
      <span className="text-sm text-muted-foreground">{size.ramGb} GB RAM</span>
      <span className="text-sm text-muted-foreground">{size.diskGb} GB</span>
      <span className="text-xs text-muted-foreground mt-2">~€{size.monthlyEstimateEur}/mo</span>
    </Label>
  )
}
```

### VmSizeQuotaInfo Component

```tsx
// src/components/requests/VmSizeQuotaInfo.tsx
import { Progress } from '@/components/ui/progress'
import { AlertTriangle } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { MockProject } from '@/lib/mock-data/projects'

interface VmSizeQuotaInfoProps {
  projectQuota: MockProject['quota'] | undefined
}

export function VmSizeQuotaInfo({ projectQuota }: VmSizeQuotaInfoProps) {
  if (!projectQuota) return null

  const remaining = projectQuota.total - projectQuota.used
  const usagePercent = projectQuota.total > 0
    ? Math.round((projectQuota.used / projectQuota.total) * 100)
    : 100
  const isWarning = usagePercent >= 80

  return (
    <div
      className={cn(
        'rounded-lg border p-4 mt-4',
        isWarning
          ? 'border-amber-500 bg-amber-50 dark:bg-amber-950/20'
          : 'border-muted'
      )}
      role="region"
      aria-live="polite"
      aria-label="Quota status"
    >
      <div className="flex items-center justify-between text-sm mb-2">
        <span>Available: {remaining} of {projectQuota.total} VMs</span>
        {isWarning && (
          <AlertTriangle className="w-4 h-4 text-amber-600 dark:text-amber-500" />
        )}
      </div>
      <Progress
        value={usagePercent}
        className={cn('h-2', isWarning && '[&>div]:bg-amber-500')}
      />
    </div>
  )
}
```

### Form Integration

```tsx
// In VmRequestForm.tsx - add size: 'M' to defaultValues
const form = useForm<VmRequestFormData>({
  resolver: zodResolver(vmRequestFormSchema),
  defaultValues: {
    vmName: '',
    projectId: '',
    justification: '',
    size: 'M',  // DEFAULT_VM_SIZE
  },
  mode: 'onChange',
})

// Replace the placeholder div with:
<FormField
  control={form.control}
  name="size"
  render={({ field }) => (
    <FormItem>
      <FormLabel>
        VM Size <span className="text-destructive">*</span>
      </FormLabel>
      <FormControl>
        <VmSizeSelector value={field.value} onValueChange={field.onChange} />
      </FormControl>
      <VmSizeQuotaInfo projectQuota={selectedProject?.quota} />
      <FormMessage />
    </FormItem>
  )}
/>
```

### Accessibility Requirements

- **RadioGroup:** Has `role="radiogroup"` with `aria-label`
- **RadioGroupItem:** Has `role="radio"` with `aria-checked`
- **Labels:** Each size card has associated label via htmlFor
- **Focus ring:** Visible focus indicator for keyboard users
- **Quota warning:** Uses `aria-live="polite"` for screen reader updates
- **Color contrast:** Warning text meets WCAG AA contrast requirements

### Testing Notes

**Key test scenarios:**
1. All 4 sizes render with correct specs
2. Default selection is "M"
3. Only one size can be selected at a time
4. Keyboard navigation works (arrow keys)
5. Quota warning shows when project selected
6. Quota exceeded warning styling applies
7. Form validation fails without size selection
8. Form submits with selected size value

**Mock data needed:**
- Reuse `MOCK_PROJECTS` from Story 2.4 for quota testing (already has VM count quota)

### Deferred Items

| Item | Reason | Target Story |
|------|--------|--------------|
| Backend size configuration | Requires API endpoint | Story 2.6+ |
| Quota enforcement on submit | Submit story scope | Story 2.6 |
| Dynamic pricing calculation | Beyond MVP scope | Post-MVP |
| Custom size request | Not in MVP requirements | Post-MVP |

### References

- [Source: docs/epics.md#Story-2.5-VM-Request-Form-Size-Selector]
- [Source: docs/ux-design-specification.md#Section-6.4-VMSizeSelector]
- [Source: docs/sprint-artifacts/2-4-vm-request-form-basic-fields.md]
- [shadcn RadioGroup Documentation](https://ui.shadcn.com/docs/components/radio-group)
- [Radix RadioGroup Accessibility](https://www.radix-ui.com/primitives/docs/components/radio-group)
- [CLAUDE.md#Zero-Tolerance-Policies]

## Story Validation

**Date:** 2025-11-30
**Validator:** SM Agent (Bob)
**Model:** claude-opus-4-5-20251101

### Checklist

- [x] All acceptance criteria are testable
- [x] Prerequisites are met (Story 2.4 done)
- [x] FRs mapped to acceptance criteria (FR18, FR83)
- [x] UX reference provided with visual spec
- [x] Technical notes include component structure
- [x] Accessibility requirements documented
- [x] Test plan covers all ACs
- [x] Deferred items clearly documented
- [x] Task breakdown is actionable

### Status

Story ready for development.

## Dev Agent Record

### Context Reference

- `docs/sprint-artifacts/2-5-vm-request-form-size-selector.context.xml` (to be generated by story-context workflow)

### Agent Model Used

TBD

### Debug Log References

TBD

### Completion Notes List

TBD

### Implementation Notes

TBD

### File List

TBD

### Code Review

TBD
