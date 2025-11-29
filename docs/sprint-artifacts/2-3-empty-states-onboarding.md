# Story 2.3: Empty States & Onboarding

Status: In-Progress

## Story

As a **new user**,
I want helpful guidance when I have no data,
So that I understand how to get started.

## Requirements Context Summary

- **Epic/AC source:** Story 2.3 in `docs/epics.md` - Empty States & Onboarding
- **FRs Satisfied:** FR85 (Empty states with guidance), FR86 (First-time user onboarding)
- **Architecture constraint:** shadcn-admin-kit patterns, localStorage for onboarding state
- **Prerequisites:** Story 2.2 (End User Dashboard Layout) - DONE
- **UX Reference:** `docs/ux-design-specification.md` Section 7.6 (Empty States)
- **Existing Code:** Dashboard components in `dvmm/dvmm-web/src/components/dashboard/`

## Acceptance Criteria

1. **Empty state for "My Requests" section**
   - Given I am a new user with zero VM requests
   - When I view the dashboard
   - Then I see an empty state illustration with:
     - Friendly message: "Noch keine VMs angefordert"
     - Clear CTA: "Erste VM anfordern" button in Tech Teal
     - Brief explanation: "Fordern Sie Ihre erste virtuelle Maschine an"
   - And the empty state uses consistent styling (Card component, centered content)

2. **Empty state for stats cards** *(Note: StatsCard already handles 0 gracefully from Story 2.2)*
   - Given I am a new user with zero activity
   - When I view the stats cards
   - Then stats show "0" with existing semantic color styling
   - And cards maintain visual hierarchy (no additional changes required)
   - *Validation: Verify existing StatsCard renders 0 correctly - no code changes needed*

3. **First-time tooltip hints on key UI elements**
   - Given I am a first-time user (no onboarding flag in localStorage)
   - When I view the dashboard
   - Then I see tooltip hints on:
     - "Request New VM" button: "Hier starten Sie eine neue VM-Anfrage"
     - Sidebar navigation: "Navigieren Sie zu Ihren Anfragen" *(desktop only)*
   - And tooltips appear with a subtle animation (fade-in)
   - And only one tooltip is visible at a time (sequential flow)
   - **Mobile behavior (< 768px):** Skip sidebar tooltip step (sidebar is hidden in Sheet)

4. **Tooltip dismissal and persistence**
   - Given I see onboarding tooltips
   - When I click "Verstanden" on a tooltip OR click elsewhere
   - Then the tooltip dismisses
   - And my progress is saved to localStorage (`dvmm_onboarding_step`)
   - And dismissed tooltips do not reappear on page refresh
   - And I can reset onboarding via a hidden dev action (Ctrl+Shift+O)

5. **Onboarding completion state**
   - Given I have dismissed all onboarding tooltips
   - When onboarding completes
   - Then localStorage key `dvmm_onboarding_completed` is set to "true"
   - And no more tooltips appear on subsequent visits
   - And the dashboard functions normally

6. **Empty state for Admin Approval Queue (scope: component only)**
   - Given I am an admin with no pending approvals
   - When I would view the approval queue (future story)
   - Then an `AdminQueueEmptyState` component exists for future use
   - And it displays: "Keine ausstehenden Genehmigungen"
   - And it shows a positive message: "Alle Anfragen wurden bearbeitet"

7. **Responsive empty states**
   - Given I view empty states on mobile
   - When the viewport is < 768px
   - Then empty state content adapts to single-column layout
   - And CTA buttons remain full-width and touch-friendly (min 44px height)
   - And illustrations scale appropriately

## Test Plan

### Unit Tests
- EmptyState component renders message, CTA, and icon
- EmptyState CTA button triggers onClick handler
- OnboardingTooltip renders with correct content
- OnboardingTooltip dismiss button calls onDismiss
- OnboardingTooltip dismisses on click-outside (Popover behavior)
- OnboardingTooltip dismisses on Escape key
- useOnboarding hook reads from localStorage correctly
- useOnboarding hook writes step progress to localStorage
- useOnboarding hook skips sidebar step on mobile viewport (< 768px)
- useOnboarding hook returns `isComplete: true` when all steps done
- AdminQueueEmptyState renders correct messaging

### Integration Tests
- Dashboard shows empty state for new user
- Full onboarding flow from start to completion

### Accessibility Tests
- Focus moves to tooltip dismiss button when tooltip appears
- Focus returns to trigger after dismissal
- Tooltips are keyboard dismissable (Escape)
- EmptyState icons have aria-hidden="true"

## Structure Alignment / Previous Learnings

### Learnings from Previous Story

#### From Story 2-2-end-user-dashboard-layout (Status: done)

- **Testing Infrastructure Established:**
  - Vitest + @testing-library/react configured
  - `vitest.config.ts` with jsdom environment
  - `src/test/setup.ts` with matchMedia mock
  - `src/test/test-utils.tsx` with MockAuthProvider

- **Component Patterns:**
  - Layout components in `components/layout/`
  - Dashboard components in `components/dashboard/`
  - shadcn components: Button, Card, Badge, Avatar, Separator, Sheet

- **Styling Patterns:**
  - Status colors via CSS variables: `--status-pending`, `--status-approved`, etc.
  - Primary color: oklch format in `index.css`
  - Tailwind 4 with CSS variables (NO tailwind.config.js)

- **RequestsPlaceholder Already Exists:**
  - `src/components/dashboard/RequestsPlaceholder.tsx` - basic placeholder
  - Needs enhancement for empty state illustration + German copy

[Source: docs/sprint-artifacts/2-2-end-user-dashboard-layout.md]

### Architecture Patterns

- **LocalStorage Pattern:** Use `useLocalStorage` hook or direct access for onboarding state
- **Component Organization:** Empty states in `components/empty-states/` or enhance existing dashboard components
- **i18n Consideration:** German text hardcoded for MVP (i18n extraction in future story)

## Tasks / Subtasks

- [x] **Task 1: Create EmptyState Component** (AC: 1, 7)
  - [x] Create `components/empty-states/EmptyState.tsx` - reusable empty state component
  - [x] Props: `icon`, `title`, `description`, `ctaLabel`, `onCtaClick`
  - [x] Add Lucide icon support (e.g., `FileQuestion`, `Inbox`, `CheckCircle2`)
  - [x] Implement responsive layout (centered, single column on mobile)
  - [x] Apply Card styling with subtle background

- [x] **Task 2: Enhance RequestsPlaceholder with Empty State** (AC: 1)
  - [x] Refactor `components/dashboard/RequestsPlaceholder.tsx` to use EmptyState
  - [x] Add German copy: "Noch keine VMs angefordert"
  - [x] Add description: "Fordern Sie Ihre erste virtuelle Maschine an"
  - [x] Add CTA button: "Erste VM anfordern"
  - [x] Wire CTA to log action (no navigation yet - Story 2.4)

- [x] **Task 3: Create Onboarding Hook** (AC: 3, 4, 5)
  - [x] Create `src/hooks/` folder (doesn't exist yet)
  - [x] Create `hooks/useOnboarding.ts` - custom hook for onboarding state
  - [x] Define onboarding steps: `['cta-button', 'sidebar-nav']`
  - [x] Read/write step progress from localStorage (`dvmm_onboarding_step`)
  - [x] Track completion state (`dvmm_onboarding_completed`)
  - [x] Expose: `currentStep`, `isComplete`, `dismissStep()`, `resetOnboarding()`
  - [x] Handle mobile viewport: skip 'sidebar-nav' step when `window.innerWidth < 768`

- [x] **Task 4: Create OnboardingTooltip Component** (AC: 3, 4)
  - [x] Create `components/onboarding/OnboardingTooltip.tsx`
  - [x] Use shadcn **Popover** (not Tooltip - Tooltip is hover-only, we need click interaction)
  - [x] Props: `targetRef`, `content`, `onDismiss`, `position`
  - [x] Add PopoverArrow for visual connection to target element
  - [x] Add fade-in animation (Tailwind `animate-fade-in` or CSS)
  - [x] Add "Verstanden" dismiss button with focus on open
  - [x] Handle click-outside to dismiss (Popover handles this automatically)
  - [x] Handle Escape key to dismiss

- [x] **Task 5: Integrate Onboarding into Dashboard** (AC: 3, 4, 5)
  - [x] Add `data-onboarding="cta-button"` to "Request New VM" button in `Dashboard.tsx`
  - [x] Add `data-onboarding="sidebar-nav"` to nav element in `Sidebar.tsx`
  - [x] Import `useOnboarding` hook in Dashboard.tsx
  - [x] Add refs to target elements using data-onboarding selectors
  - [x] Render OnboardingTooltip based on `currentStep`
  - [x] Wire dismiss actions to `dismissStep()`
  - [x] Add keyboard shortcut (Ctrl+Shift+O) for reset in dev mode

- [x] **Task 6: Create AdminQueueEmptyState** (AC: 6)
  - [x] Create `components/empty-states/AdminQueueEmptyState.tsx`
  - [x] Display: "Keine ausstehenden Genehmigungen"
  - [x] Positive message: "Alle Anfragen wurden bearbeitet"
  - [x] Use CheckCircle2 icon in Emerald color
  - [x] Export for future Admin Queue story

- [x] **Task 7: Write Tests** (Test Plan)
  - [x] Unit test: EmptyState renders title, description, icon, CTA
  - [x] Unit test: EmptyState CTA click calls handler
  - [x] Unit test: OnboardingTooltip renders content and dismiss button
  - [x] Unit test: OnboardingTooltip dismiss button calls onDismiss
  - [x] Unit test: OnboardingTooltip dismisses on click-outside
  - [x] Unit test: OnboardingTooltip dismisses on Escape key
  - [x] Unit test: useOnboarding returns correct initial state from localStorage
  - [x] Unit test: useOnboarding.dismissStep advances step
  - [x] Unit test: useOnboarding skips sidebar step on mobile viewport
  - [x] Unit test: useOnboarding.resetOnboarding clears localStorage
  - [x] Unit test: AdminQueueEmptyState renders correctly
  - [x] Integration test: Dashboard shows empty state for new user
  - [x] Integration test: Full onboarding flow from start to completion

## Dev Notes

### Existing Files to Modify

| File | Changes Required |
|------|------------------|
| `src/pages/Dashboard.tsx` | Add `data-onboarding="cta-button"` to Button, import useOnboarding, render tooltips |
| `src/components/layout/Sidebar.tsx` | Add `data-onboarding="sidebar-nav"` to nav element |
| `src/components/dashboard/RequestsPlaceholder.tsx` | Replace content with EmptyState component |
| `src/index.css` | Add `.animate-fade-in` keyframes |

### Component Structure (Target)

```
dvmm/dvmm-web/src/
├── components/
│   ├── empty-states/
│   │   ├── EmptyState.tsx           # Reusable empty state component
│   │   ├── AdminQueueEmptyState.tsx # Admin-specific empty state
│   │   └── index.ts                 # Barrel export
│   ├── onboarding/
│   │   ├── OnboardingTooltip.tsx    # Positioned tooltip component
│   │   ├── OnboardingProvider.tsx   # Context provider (optional)
│   │   └── index.ts                 # Barrel export
│   └── dashboard/
│       └── RequestsPlaceholder.tsx  # Enhanced with EmptyState
├── hooks/
│   └── useOnboarding.ts             # Onboarding state management
└── pages/
    └── Dashboard.tsx                # Integrates onboarding
```

### shadcn Components to Add

```bash
# From dvmm/dvmm-web directory
npx shadcn@latest add popover     # REQUIRED: For OnboardingTooltip (supports click interaction + dismiss button)
# DO NOT use 'tooltip' - it's hover-only and won't work for our use case
```

### LocalStorage Keys

| Key | Type | Description |
|-----|------|-------------|
| `dvmm_onboarding_step` | `number` | Current step index (0-based) |
| `dvmm_onboarding_completed` | `"true" \| null` | Flag for completion |

### Onboarding Steps Definition

```typescript
const ONBOARDING_STEPS = [
  {
    id: 'cta-button',
    targetSelector: '[data-onboarding="cta-button"]',
    content: 'Hier starten Sie eine neue VM-Anfrage',
    position: 'bottom' as const,
  },
  {
    id: 'sidebar-nav',
    targetSelector: '[data-onboarding="sidebar-nav"]',
    content: 'Navigieren Sie zu Ihren Anfragen',
    position: 'right' as const,
  },
] as const;
```

### EmptyState Component API

```tsx
interface EmptyStateProps {
  icon?: LucideIcon;
  title: string;
  description?: string;
  ctaLabel?: string;
  onCtaClick?: () => void;
  className?: string;
}

// Usage
<EmptyState
  icon={FileQuestion}
  title="Noch keine VMs angefordert"
  description="Fordern Sie Ihre erste virtuelle Maschine an"
  ctaLabel="Erste VM anfordern"
  onCtaClick={() => console.log('Navigate to VM request form')}
/>
```

### OnboardingTooltip Component API

```tsx
interface OnboardingTooltipProps {
  targetRef: RefObject<HTMLElement>;
  content: string;
  onDismiss: () => void;
  position?: 'top' | 'right' | 'bottom' | 'left';
  dismissLabel?: string;
}

// Implementation uses shadcn Popover with arrow
import { Popover, PopoverContent, PopoverTrigger, PopoverAnchor } from '@/components/ui/popover'

// Usage pattern
<Popover open={isOpen} onOpenChange={handleOpenChange}>
  <PopoverAnchor virtualRef={targetRef} />
  <PopoverContent side={position} sideOffset={8} className="animate-fade-in">
    <p className="text-sm">{content}</p>
    <Button size="sm" onClick={onDismiss} className="mt-2 w-full">
      {dismissLabel}
    </Button>
  </PopoverContent>
</Popover>
```

### Fade-In Animation (CSS)

Add to `index.css` or create utility class:

```css
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: fadeIn 0.2s ease-out;
}
```

### Keyboard Reset Handler

```typescript
// In Dashboard.tsx useEffect
useEffect(() => {
  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.ctrlKey && e.shiftKey && e.key === 'O') {
      onboarding.resetOnboarding();
      console.log('[Dev] Onboarding reset');
    }
  };

  if (import.meta.env.DEV) {
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }
}, [onboarding]);
```

### Testing LocalStorage

```typescript
// In test setup, mock localStorage
const localStorageMock = {
  store: {} as Record<string, string>,
  getItem: vi.fn((key: string) => localStorageMock.store[key] ?? null),
  setItem: vi.fn((key: string, value: string) => {
    localStorageMock.store[key] = value;
  }),
  removeItem: vi.fn((key: string) => {
    delete localStorageMock.store[key];
  }),
  clear: vi.fn(() => {
    localStorageMock.store = {};
  }),
};

Object.defineProperty(window, 'localStorage', { value: localStorageMock });

// Reset between tests
beforeEach(() => {
  localStorageMock.clear();
  vi.clearAllMocks();
});
```

### Accessibility Requirements

- **Focus management:** Focus must move to tooltip dismiss button when tooltip appears
- **Focus return:** Focus returns to trigger element after dismissal
- **Keyboard:** Tooltips must be dismissable via Escape key
- **Focus indicators:** CTA buttons must have visible focus indicators
- **Decorative icons:** Empty state icons need `aria-hidden="true"` (decorative)
- **Screen readers:** Tooltip content should be announced (use `role="dialog"` with `aria-labelledby`)

### Deferred Items

| Item | Reason | Target Story |
|------|--------|--------------|
| i18n extraction | MVP uses hardcoded German | Future (i18n story) |
| Onboarding analytics | Track completion rates | Post-MVP |
| Admin queue integration | Empty state component only | Story 2.8+ |
| Illustration SVGs | Using Lucide icons for MVP | Polish phase |

### References

- [Source: docs/epics.md#Story-2.3-Empty-States-Onboarding]
- [Source: docs/ux-design-specification.md#Section-7.6-Empty-States]
- [Source: docs/sprint-artifacts/2-2-end-user-dashboard-layout.md#Dev-Agent-Record]
- [Source: CLAUDE.md#Zero-Tolerance-Policies]

## Party Mode Review

**Date:** TBD
**Participants:** TBD

## Story Validation

**Date:** 2025-11-29
**Validator:** SM Agent (Bob) - Fresh Context Validation
**Model:** claude-opus-4-5-20251101

### Initial Findings

| Category | Count |
|----------|-------|
| Critical Issues | 4 |
| Enhancements | 5 |
| Optimizations | 2 |

### Issues Fixed

1. ✅ **Missing data-onboarding attributes** → Added explicit guidance in Task 5
2. ✅ **Mobile onboarding edge case** → Added mobile behavior to AC #3 and Task 3
3. ✅ **Missing click-outside test** → Added to Task 7 test list
4. ✅ **AC #2 already satisfied** → Clarified as validation-only (no code changes)
5. ✅ **Missing existing file paths** → Added "Existing Files to Modify" section
6. ✅ **Hooks folder creation** → Added to Task 3
7. ✅ **shadcn component choice** → Clarified: use Popover, not Tooltip
8. ✅ **Focus management** → Added to Accessibility Requirements
9. ✅ **Tooltip implementation pattern** → Added PopoverAnchor usage example

### Final Status

All critical issues resolved. Story ready for development.

## Dev Agent Record

### Context Reference

- `docs/sprint-artifacts/2-3-empty-states-onboarding.context.xml` (to be generated by story-context workflow)

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
