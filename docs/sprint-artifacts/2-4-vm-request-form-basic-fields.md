# Story 2.4: VM Request Form - Basic Fields

Status: done

## Story

As an **end user**,
I want to enter basic information for my VM request,
So that I can specify what I need.

## Requirements Context Summary

- **Epic/AC source:** Story 2.4 in `docs/epics.md` - VM Request Form - Basic Fields
- **FRs Satisfied:** FR17 (VM Name Validation), FR19 (Justification Field)
- **Architecture constraint:** React Hook Form + Zod validation, shadcn-admin-kit patterns
- **Prerequisites:** Story 2.2 (End User Dashboard Layout) - DONE, Story 2.3 (Empty States) - DONE
- **Routing Note:** This story introduces React Router to the project (first multi-page navigation)
- **UX Reference:** `docs/ux/design-specification.md` Section 3 (Visual Foundation), custom VM Size Selector component
- **Tech Spec Reference:** `docs/sprint-artifacts/tech-spec-epic-2.md` Section 4.4 (Frontend Architecture)
- **Existing Code:** Dashboard + layout components in `dcm/dcm-web/src/`

## Pre-Flight Setup Checklist

Before starting implementation, verify these are complete:

- [ ] **React Router installed:** `npm list react-router-dom` shows version 6.x+
- [ ] **Form libraries installed:** `npm list react-hook-form zod @hookform/resolvers`
- [ ] **shadcn components added:** input, textarea, select, label, form, progress in `src/components/ui/`
- [ ] **Folder structure created:** `src/components/requests/`, `src/lib/validations/`, `src/components/auth/`
- [ ] **App.tsx has BrowserRouter:** Routing infrastructure is in place

If any items are missing, complete Tasks 1-2 first before proceeding.

## Acceptance Criteria

1. **Navigation to request form**
   - Given I am on the dashboard (authenticated)
   - When I click "Request New VM" button (the CTA button with `data-onboarding="cta-button"`)
   - Then I am navigated to `/requests/new`
   - And the VM request form is displayed
   - And the page title shows "Request New VM"

2. **VM Name field with validation**
   - Given I am on the VM request form
   - When I view the VM Name field
   - Then I see a text input with label "VM Name" and placeholder "e.g. web-server-01"
   - And the field is marked as required
   - And there is a help text explaining naming rules: "3-63 characters, lowercase letters, numbers, and hyphens"

   **Validation rules (inline as I type):**
   - When I enter less than 3 characters → error: "Minimum 3 characters required"
   - When I enter more than 63 characters → error: "Maximum 63 characters allowed"
   - When I enter uppercase letters → error: "Only lowercase letters allowed"
   - When I enter spaces or special chars → error: "Only letters, numbers, and hyphens allowed"
   - When I start with a hyphen → error: "Must start with a letter or number"
   - When I end with a hyphen → error: "Must end with a letter or number"
   - When value matches regex `^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$` → no error, field shows valid state

3. **Project dropdown**
   - Given I am on the VM request form
   - When I view the Project field
   - Then I see a dropdown/select with label "Project" marked as required
   - And the dropdown is populated with my accessible projects (mock data for now)
   - And each option shows: `{projectName}` with quota info as secondary text
   - And there is a "No suitable project?" link below the dropdown

   **Mock project data (until backend integration):**
   ```typescript
   const MOCK_PROJECTS = [
     { id: 'proj-1', name: 'Development', quota: { used: 5, total: 10 } },
     { id: 'proj-2', name: 'Production', quota: { used: 8, total: 10 } },
     { id: 'proj-3', name: 'Testing', quota: { used: 0, total: 5 } },
   ];
   ```

4. **Project quota display on selection**
   - Given I select a project from the dropdown
   - When the selection is confirmed
   - Then I see the project's remaining quota displayed below the dropdown
   - And the quota shows: "Available: X of Y VMs" with a progress bar
   - And if quota is nearly exhausted (>80%), the text is styled in warning color (Orange)

5. **Justification field with validation**
   - Given I am on the VM request form
   - When I view the Justification field
   - Then I see a textarea with label "Justification" marked as required
   - And there is a placeholder: "Describe the purpose of this VM..."
   - And there is a character counter showing current/minimum (e.g., "0/10 characters")

   **Validation rules:**
   - When I enter less than 10 characters → error: "Minimum 10 characters required"
   - When I enter 10+ characters → no error, field shows valid state
   - Max length: 1000 characters (soft limit with counter, hard limit on submit)

6. **Form state persistence (warn on leave)**
   - Given I have entered data in any form field
   - When I attempt to navigate away (browser back, click other link, close tab)
   - Then I see a browser confirmation dialog: "Changes will not be saved. Continue?"
   - And if I confirm, navigation proceeds and form data is lost
   - And if I cancel, I stay on the form with data preserved

7. **Form layout and responsiveness**
   - Given I view the form on desktop (≥768px)
   - Then the form is centered with max-width ~640px
   - And fields are stacked vertically with consistent spacing (24px gap)
   - And the submit button section is at the bottom (Story 2.6 placeholder)

   - Given I view the form on mobile (<768px)
   - Then the form fills the available width with padding
   - And fields remain touch-friendly (min 44px height for inputs)

8. **"No Project" help link**
   - Given I see the "No suitable project?" link
   - When I click the link
   - Then a tooltip or small dialog appears explaining: "Contact your admin to request project access"
   - And the dialog can be dismissed by clicking outside or pressing Escape

## Test Plan

### Unit Tests

**VmRequestForm component:**
- Renders all form fields (vmName, project, justification)
- Renders required indicators on all fields
- Renders help text for VM name field
- Renders character counter for justification field

**VM Name validation (Zod schema):**
- Rejects empty string
- Rejects strings < 3 characters
- Rejects strings > 63 characters
- Rejects uppercase letters
- Rejects spaces and special characters
- Rejects strings starting with hyphen
- Rejects strings ending with hyphen
- Accepts valid names (e.g., "web-server-01", "db1", "my-app-123")

**Justification validation (Zod schema):**
- Rejects empty string
- Rejects strings < 10 characters
- Accepts strings ≥ 10 characters
- Rejects strings > 1000 characters

**Project dropdown:**
- Renders all mock projects
- Shows quota info for each project
- Updates quota display on selection
- Shows warning styling when quota > 80%

**useFormPersistence hook (warn on leave):**
- Returns true when form is dirty
- Returns false when form is clean
- Triggers beforeunload event when dirty

**NoProjectHelpDialog component:**
- Renders on link click
- Dismisses on outside click
- Dismisses on Escape key

### Integration Tests

- Full form renders with all fields and validation
- Form validation prevents invalid submission
- Navigation to /requests/new from dashboard CTA works
- Form dirty state triggers navigation warning

### Accessibility Tests

- All form fields have associated labels
- Error messages are announced to screen readers (aria-describedby)
- Required fields have aria-required="true"
- Focus moves to first error on validation failure
- Character counter is accessible (aria-live="polite")

## Structure Alignment / Previous Learnings

### Learnings from Previous Story

#### From Story 2-3-empty-states-onboarding (Status: done)

- **Hooks pattern established:**
  - Custom hooks in `src/hooks/` folder
  - useOnboarding pattern for localStorage state management
  - Same pattern can be used for form persistence

- **Component organization:**
  - Reusable components in `components/{feature}/`
  - Index.ts barrel exports for clean imports
  - Test files co-located with components

- **Testing patterns:**
  - Vitest + @testing-library/react configured
  - MockAuthProvider in test-utils.tsx
  - localStorage mock pattern for state management

- **Styling patterns:**
  - Tailwind 4 with CSS variables in `index.css`
  - Status colors via CSS variables
  - Animation keyframes in index.css

- **Navigation:**
  - React Router for page navigation
  - Dashboard at `/`, new pages at `/{feature}/{action}`

[Source: docs/sprint-artifacts/2-3-empty-states-onboarding.md]

#### From Story 2-2-end-user-dashboard-layout (Status: done)

- **shadcn components available:**
  - Button, Card, Badge, Avatar, Separator, Sheet, Popover
  - Need to add: Input, Textarea, Select, Label, Form

- **Layout established:**
  - DashboardLayout wraps all authenticated pages
  - Header with user info, Sidebar with navigation
  - Main content area with consistent padding

[Source: docs/sprint-artifacts/2-2-end-user-dashboard-layout.md]

### Architecture Patterns

- **Form Validation:** React Hook Form + Zod for type-safe schema validation
- **Component Composition:** shadcn/ui form components with custom styling
- **State Management:** Form state via react-hook-form, navigation warnings via browser API
- **Routing:** React Router for `/requests/new` page

## Tasks / Subtasks

- [x] **Task 1: Add required shadcn components** (Setup)
  - [x] Run `npx shadcn@latest add input textarea select label form progress`
  - [x] Verify components added to `src/components/ui/`
  - [x] Verify form component includes React Hook Form integration
  - [x] Note: Progress component is required for quota visualization (AC #4)

- [x] **Task 2: Install React Router, React Hook Form, and Zod** (Setup - CRITICAL)
  - [x] Run `npm install react-router-dom react-hook-form @hookform/resolvers zod`
  - [x] Verify packages in package.json
  - [x] Create folder structure:
    ```bash
    mkdir -p src/components/requests
    mkdir -p src/components/auth
    mkdir -p src/lib/validations
    mkdir -p src/lib/mock-data
    ```
  - [x] **Note:** React 19 is compatible with React Hook Form 7.x and react-router-dom 6.x

- [x] **Task 3: Create VM Request Form validation schema** (AC: 2, 5)
  - [x] Create `src/lib/validations/vm-request.ts`
  - [x] Define `vmNameSchema` with regex and error messages
  - [x] Define `justificationSchema` with min/max length
  - [x] Define `projectSchema` for required selection
  - [x] Export `vmRequestFormSchema` combining all fields
  - [x] Write unit tests for each validation rule

- [x] **Task 4: Create VmRequestForm component** (AC: 1, 2, 5, 7)
  - [x] Create `src/components/requests/VmRequestForm.tsx`
  - [x] Integrate React Hook Form with Zod resolver
  - [x] Add VM Name field with help text and inline validation
  - [x] Add Justification textarea with character counter
  - [x] Add placeholder for Size Selector (Story 2.5)
  - [x] Add placeholder for Submit button (Story 2.6)
  - [x] Style with responsive layout (max-w-xl, centered)
  - [x] Write unit tests for field rendering

- [x] **Task 5: Create ProjectSelect component** (AC: 3, 4)
  - [x] Create `src/components/requests/ProjectSelect.tsx`
  - [x] Use shadcn Select with custom option rendering
  - [x] Display project name with quota as secondary text
  - [x] Show quota progress bar on selection
  - [x] Style warning color when quota > 80%
  - [x] Add "No suitable project?" link
  - [x] Use mock data constant (MOCK_PROJECTS)
  - [x] Write unit tests for rendering and selection

- [x] **Task 6: Create useFormPersistence hook** (AC: 6)
  - [x] Create `src/hooks/useFormPersistence.ts`
  - [x] Accept `isDirty` boolean from react-hook-form
  - [x] Add beforeunload event listener when dirty
  - [x] Return cleanup function
  - [x] Write unit tests for event listener behavior

- [x] **Task 7: Create NoProjectHelpDialog component** (AC: 8)
  - [x] Create `src/components/requests/NoProjectHelpDialog.tsx`
  - [x] Use shadcn Popover (same pattern as OnboardingTooltip)
  - [x] Display help message
  - [x] Handle outside click and Escape dismissal
  - [x] Write unit tests for open/close behavior

- [x] **Task 8: Create NewRequest page** (AC: 1)
  - [x] Create `src/pages/NewRequest.tsx`
  - [x] Wrap with DashboardLayout
  - [x] Add page header: "Request New VM"
  - [x] Render VmRequestForm component
  - [x] Integrate useFormPersistence hook

- [x] **Task 9: Add routing infrastructure and /requests/new route** (AC: 1 - CRITICAL)
  - [x] Create `src/components/auth/ProtectedRoute.tsx` (see Dev Notes for implementation)
  - [x] **Refactor App.tsx to use React Router:**
    - [x] Wrap app content with `<BrowserRouter>`
    - [x] Create `<Routes>` with route definitions
    - [x] Move authenticated content into routes
  - [x] Add route for `/requests/new` → NewRequest page with ProtectedRoute wrapper
  - [x] **Important:** Current App.tsx renders Dashboard directly without routing - needs full restructure

- [x] **Task 10: Wire dashboard CTA to navigate** (AC: 1)
  - [x] Update Dashboard.tsx CTA button with navigation
  - [x] Add onClick navigation to `/requests/new`
  - [x] Use React Router's useNavigate hook

- [x] **Task 11: Write integration tests** (Test Plan)
  - [x] Test full form renders with validation
  - [x] Test navigation from dashboard to form
  - [x] Test form dirty state warning

## Dev Notes

### Existing Files to Modify

| File | Changes Required |
|------|------------------|
| `src/pages/Dashboard.tsx` | Add onClick navigate to CTA button |
| `src/App.tsx` | Add route for `/requests/new` |

### Component Structure (Target)

```
dcm/dcm-web/src/
├── components/
│   ├── auth/
│   │   ├── ProtectedRoute.tsx      # Route guard (NEW - required for routing)
│   │   └── index.ts                # Barrel export
│   ├── ui/
│   │   ├── input.tsx          # shadcn (to add)
│   │   ├── textarea.tsx       # shadcn (to add)
│   │   ├── select.tsx         # shadcn (to add)
│   │   ├── label.tsx          # shadcn (to add)
│   │   ├── form.tsx           # shadcn (to add)
│   │   └── progress.tsx       # shadcn (to add - for quota bar)
│   └── requests/
│       ├── VmRequestForm.tsx       # Main form component
│       ├── VmRequestForm.test.tsx
│       ├── ProjectSelect.tsx       # Project dropdown with quota
│       ├── ProjectSelect.test.tsx
│       ├── NoProjectHelpDialog.tsx # Help popover
│       ├── NoProjectHelpDialog.test.tsx
│       └── index.ts                # Barrel export
├── hooks/
│   ├── useOnboarding.ts       # From Story 2.3
│   └── useFormPersistence.ts  # New hook
├── lib/
│   ├── mock-data/
│   │   └── projects.ts        # Mock project data
│   └── validations/
│       ├── vm-request.ts      # Zod schemas
│       └── vm-request.test.ts
├── pages/
│   ├── Dashboard.tsx          # Existing (modify - add useNavigate)
│   └── NewRequest.tsx         # New page
└── types/
    └── index.ts               # Add form types
```

### shadcn Components to Add

```bash
# From dcm/dcm-web directory
npx shadcn@latest add input textarea select label form progress
```

### Mock Project Data

```typescript
// src/lib/mock-data/projects.ts
export interface MockProject {
  id: string;
  name: string;
  quota: {
    used: number;
    total: number;
  };
}

export const MOCK_PROJECTS: MockProject[] = [
  { id: 'proj-1', name: 'Development', quota: { used: 5, total: 10 } },
  { id: 'proj-2', name: 'Production', quota: { used: 8, total: 10 } },
  { id: 'proj-3', name: 'Testing', quota: { used: 0, total: 5 } },
];
```

### Zod Schema Definition

```typescript
// src/lib/validations/vm-request.ts
import { z } from 'zod';

// VM Name: 3-63 chars, lowercase alphanumeric and hyphens
// Must start and end with alphanumeric
// Using superRefine for granular error messages (better UX than regex-only)
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
      });
    }
    // Check for invalid characters (spaces, special chars)
    if (/[^a-z0-9-]/.test(val)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Only letters, numbers, and hyphens allowed',
      });
    }
    // Check start character
    if (val.startsWith('-')) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Must start with a letter or number',
      });
    }
    // Check end character
    if (val.endsWith('-')) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Must end with a letter or number',
      });
    }
  });

export const justificationSchema = z
  .string()
  .min(10, 'Minimum 10 characters required')
  .max(1000, 'Maximum 1000 characters allowed');

export const projectIdSchema = z
  .string()
  .min(1, 'Project is required');

export const vmRequestFormSchema = z.object({
  vmName: vmNameSchema,
  projectId: projectIdSchema,
  justification: justificationSchema,
  // size will be added in Story 2.5
});

export type VmRequestFormData = z.infer<typeof vmRequestFormSchema>;
```

### React Hook Form Integration

```tsx
// In VmRequestForm.tsx
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { vmRequestFormSchema, VmRequestFormData } from '@/lib/validations/vm-request';

export function VmRequestForm() {
  const form = useForm<VmRequestFormData>({
    resolver: zodResolver(vmRequestFormSchema),
    defaultValues: {
      vmName: '',
      projectId: '',
      justification: '',
    },
    mode: 'onChange', // Validate on change for inline errors
  });

  const onSubmit = (data: VmRequestFormData) => {
    // Will be implemented in Story 2.6
    console.log('Form submitted:', data);
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        {/* Fields here */}
      </form>
    </Form>
  );
}
```

### useFormPersistence Hook

```typescript
// src/hooks/useFormPersistence.ts
import { useEffect } from 'react';

export function useFormPersistence(isDirty: boolean) {
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isDirty) {
        e.preventDefault();
        e.returnValue = 'Changes will not be saved. Continue?';
      }
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [isDirty]);
}
```

**Testing Note:** The `beforeunload` event has limited testability in JSDOM. Tests should verify:
1. Event listener is added when isDirty=true
2. Event listener is removed on cleanup
3. Handler calls `e.preventDefault()` when isDirty=true

Use `vi.spyOn(window, 'addEventListener')` to verify listener registration.

### Character Counter Component

```tsx
// Part of justification field
<div className="text-sm text-muted-foreground text-right">
  <span
    className={cn(
      watch('justification').length < 10 && 'text-destructive'
    )}
  >
    {watch('justification').length}
  </span>
  /10 characters (min)
</div>
```

### Accessibility Requirements

- **Labels:** Every form field must have an associated `<label>` with `htmlFor`
- **Errors:** Use `aria-describedby` to link error messages to fields
- **Required:** Add `aria-required="true"` to required fields
- **Focus:** On validation failure, focus first field with error
- **Live regions:** Character counter uses `aria-live="polite"`

### ProtectedRoute Component (Required)

```tsx
// src/components/auth/ProtectedRoute.tsx
import { useAuth } from 'react-oidc-context'
import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'

interface ProtectedRouteProps {
  children: ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const auth = useAuth()
  const location = useLocation()

  // Show loading while checking auth
  if (auth.isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>
    )
  }

  // Redirect to login if not authenticated
  if (!auth.isAuthenticated) {
    // Trigger Keycloak login
    auth.signinRedirect({ state: { returnTo: location.pathname } })
    return null
  }

  return <>{children}</>
}
```

### App.tsx Router Setup (Full Refactor Required)

**Current State:** App.tsx renders `<Dashboard />` directly without routing.

**Target State:** Full React Router integration with multiple pages.

```tsx
// src/App.tsx - REFACTORED VERSION
import { useEffect } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { DashboardLayout } from '@/components/layout'
import { ProtectedRoute } from '@/components/auth/ProtectedRoute'
import { Dashboard } from '@/pages/Dashboard'
import { NewRequest } from '@/pages/NewRequest'
import { fetchCsrfToken, clearCsrfToken } from '@/api/api-client'

function AppRoutes() {
  const auth = useAuth()

  // Fetch CSRF token when authenticated
  useEffect(() => {
    if (auth.isAuthenticated && auth.user?.access_token) {
      fetchCsrfToken(auth.user.access_token).catch((error) => {
        console.error('Failed to fetch CSRF token:', error)
      })
    }
    return () => {
      if (!auth.isAuthenticated) {
        clearCsrfToken()
      }
    }
  }, [auth.isAuthenticated, auth.user?.access_token])

  // Handle OIDC callback processing
  if (auth.isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto mb-4" />
          <p className="text-muted-foreground">Loading...</p>
        </div>
      </div>
    )
  }

  // Handle authentication errors
  if (auth.error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center max-w-md p-6">
          <h1 className="text-2xl font-bold text-destructive mb-4">Authentication Error</h1>
          <p className="text-muted-foreground mb-6">{auth.error.message}</p>
          <button onClick={() => auth.signinRedirect()}>Try Again</button>
        </div>
      </div>
    )
  }

  // Main authenticated routes
  return (
    <Routes>
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <DashboardLayout>
              <Dashboard />
            </DashboardLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/requests/new"
        element={
          <ProtectedRoute>
            <DashboardLayout>
              <NewRequest />
            </DashboardLayout>
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </ErrorBoundary>
  )
}

export default App
```

**Key Changes from Current App.tsx:**
1. Added `BrowserRouter` wrapper
2. Created separate `AppRoutes` component for route definitions
3. Moved `DashboardLayout` into route elements (not global wrapper)
4. Added `ProtectedRoute` wrapper for authenticated routes
5. Added `/requests/new` route for the new request form

### Deferred Items

| Item | Reason | Target Story |
|------|--------|--------------|
| Size Selector | Separate story for visual complexity | Story 2.5 |
| Submit logic | Requires backend command handler | Story 2.6 |
| Real project data | Requires ProjectQueryService | Story 2.6+ |
| Form data persistence to localStorage | Scope creep, beforeunload sufficient | Future |

### Architectural Watch Items (from Party Mode Review)

| Item | Concern | Action Required | Target |
|------|---------|-----------------|--------|
| AuthGuard extraction | Auth loading states are inside AppRoutes; as routes grow, extract dedicated AuthGuard component | Refactor when adding 3+ routes | Story 2.7+ |
| OIDC callback routes | `/callback` and `/silent-renew` routes needed for Keycloak token refresh | **MUST add before backend integration** | Story 2.6 |

**Note:** These items were identified by Winston (Architect) during Party Mode review on 2025-11-30.

### References

- [Source: docs/epics.md#Story-2.4-VM-Request-Form-Basic-Fields]
- [Source: docs/sprint-artifacts/tech-spec-epic-2.md#Section-4.4-Frontend-Architecture]
- [Source: docs/ux/design-specification.md#Section-3-Visual-Foundation]
- [Source: docs/sprint-artifacts/2-3-empty-states-onboarding.md#Dev-Agent-Record]
- [React Hook Form Documentation](https://react-hook-form.com/)
- [Zod Documentation](https://zod.dev/)
- [CLAUDE.md#Zero-Tolerance-Policies]

## Party Mode Review

**Date:** 2025-11-30
**Participants:** Bob (SM), Winston (Architect), Amelia (Dev), Sally (UX), Murat (TEA)

### Key Discussion Points

1. **Validation Review** - Bob confirmed 8 issues fixed, story ready-for-dev
2. **Architecture Concerns** - Winston flagged AuthGuard extraction and OIDC callback routes (captured in Architectural Watch Items)
3. **Implementation Order** - Amelia will reorder tasks: routing infra (Task 9) before form components (Tasks 3-8)
4. **UX Feedback** - Sally reviewed form design
5. **Test Strategy** - Murat reviewed testing approach

### Sally (UX Designer) Feedback

**Positive:**
- Inline validation gives immediate feedback - reduces "submit and pray" anxiety
- Character counter shows progress toward minimum - users feel completion
- Clear English error messages reduce cognitive load
- "No suitable project?" help link acknowledges user frustration proactively

**Concerns for Future Polish:**
| Item | Note | Priority |
|------|------|----------|
| Quota 100% behavior | What happens when project quota is exhausted? Can user still select? Add disabled state. | Post-MVP |
| Browser navigation dialog | Native dialogs are ugly - consider custom modal matching design system | Post-MVP |
| Field order | Consider Project → VM Name → Justification (users often know project first) | Polish phase |

**Accessibility:** Confirmed `aria-describedby` for errors and `aria-live="polite"` for character counter are correct.

### Murat (Test Architect) Feedback

**Risk Assessment:**
| Component | Risk | Reason |
|-----------|------|--------|
| Zod validation | LOW | Pure functions, deterministic |
| React Hook Form | MEDIUM | State management, dirty/pristine transitions |
| App.tsx routing refactor | **HIGH** | Touches auth flow, could break existing |
| beforeunload hook | MEDIUM | Browser API, limited JSDOM support |

**Testing Strategy:**
1. **App.tsx refactor - write regression tests FIRST** (non-negotiable):
   - `renders Dashboard when authenticated`
   - `redirects to Keycloak when not authenticated`
   - `shows loading spinner during auth check`
   - `displays error on auth failure`
2. **Validation schema** - 100% branch coverage, one test per `addIssue` call
3. **Integration priority:** Dashboard CTA navigation (HIGH), form dirty state (MEDIUM)
4. **Skip for MVP:** E2E tests (save for Story 2.6), visual regression tests

**Flakiness Warning:** `beforeunload` tests can cause CI flakiness. Mock `window.addEventListener` rather than triggering actual event.

### Amelia (Developer) Implementation Order

**Reordered execution sequence:**
```
Task 2 (npm install + mkdir) → Task 1 (shadcn) → Task 9 (routing infra) → Task 3-8 (form components) → Task 10-11 (integration)
```
**Rationale:** Routing infrastructure MUST exist before testing navigation from Dashboard.

## Story Validation

**Date:** 2025-11-30
**Validator:** SM Agent (Bob) - Fresh Context Validation
**Model:** claude-opus-4-5-20251101

### Initial Findings

| Category | Count |
|----------|-------|
| Critical Issues | 5 |
| Enhancements | 4 |
| Optimizations | 2 |

### Issues Fixed

1. ✅ **React Router not installed** → Added to Task 2 with npm install command
2. ✅ **App.tsx has no router setup** → Added complete App.tsx refactor example in Dev Notes
3. ✅ **Missing folders (requests/, lib/validations/)** → Added mkdir commands to Task 2
4. ✅ **Zod schema had redundant regex + refines** → Rewrote using superRefine for clean granular errors
5. ✅ **ProtectedRoute component missing** → Added full implementation in Dev Notes
6. ✅ **Routing prerequisite not mentioned** → Added "Routing Note" to Requirements Context
7. ✅ **React 19 compatibility unclear** → Added compatibility note to Task 2
8. ✅ **No pre-flight checklist** → Added "Pre-Flight Setup Checklist" section

### Final Status

All critical issues resolved. Story ready for development.

## Dev Agent Record

### Context Reference

- `docs/sprint-artifacts/2-4-vm-request-form-basic-fields.context.xml` (to be generated by story-context workflow)

### Agent Model Used

claude-opus-4-5-20251101

### Debug Log References

N/A

### Completion Notes List

- All 8 acceptance criteria implemented and tested
- 163 unit/integration tests passing
- Build succeeds with only expected React Compiler warning (React Hook Form incompatibility)
- PR review findings addressed: division by zero fix, console.info logging, HTML nesting fix

### Implementation Notes

**Key Implementation Decisions:**

1. **Zod Validation with superRefine**: Used `superRefine` for granular error messages instead of chained `.refine()` calls - cleaner code, better UX
2. **React Hook Form mode: onChange**: Enables inline validation as user types (AC #2)
3. **Division by zero guard**: ProjectQuotaDisplay treats zero-quota projects as 100% used
4. **beforeunload hook**: useFormPersistence uses browser API for navigation warnings
5. **CTA button language**: Dashboard uses English "Request New VM" and page title uses English "Request New VM" (consistent with existing English UI)

**Deferred to Future Stories:**
- VM Size Selector (Story 2.5 placeholder in form)
- Submit button logic (Story 2.6 placeholder in form)
- E2E tests with Playwright (Story 2.6)

### File List

**New Files Created:**
- `src/components/requests/VmRequestForm.tsx` - Main form component with React Hook Form
- `src/components/requests/VmRequestForm.test.tsx` - 18 unit tests
- `src/components/requests/ProjectSelect.tsx` - Project dropdown with quota display
- `src/components/requests/ProjectSelect.test.tsx` - 13 unit tests
- `src/components/requests/NoProjectHelpDialog.tsx` - Help popover component
- `src/components/requests/NoProjectHelpDialog.test.tsx` - 7 unit tests
- `src/components/requests/index.ts` - Barrel exports
- `src/components/auth/ProtectedRoute.tsx` - Route guard component
- `src/components/auth/ProtectedRoute.test.tsx` - 3 unit tests
- `src/components/auth/index.ts` - Barrel exports
- `src/hooks/useFormPersistence.ts` - beforeunload hook
- `src/hooks/useFormPersistence.test.ts` - 5 unit tests
- `src/lib/validations/vm-request.ts` - Zod validation schemas
- `src/lib/validations/vm-request.test.ts` - 34 unit tests
- `src/lib/mock-data/projects.ts` - Mock project data
- `src/pages/NewRequest.tsx` - New request page
- `src/components/ui/input.tsx` - shadcn component
- `src/components/ui/textarea.tsx` - shadcn component
- `src/components/ui/select.tsx` - shadcn component
- `src/components/ui/label.tsx` - shadcn component
- `src/components/ui/form.tsx` - shadcn component
- `src/components/ui/progress.tsx` - shadcn component

**Modified Files:**
- `src/App.tsx` - Added React Router, routes for / and /requests/new
- `src/pages/Dashboard.tsx` - Added useNavigate, CTA onClick navigation
- `src/__tests__/App.integration.test.tsx` - Added NewRequest route test
- `package.json` - Added react-router-dom, react-hook-form, @hookform/resolvers, zod

### Code Review

**Date:** 2025-11-30
**Reviewer:** BMAD Code Review Workflow (Adversarial)
**Model:** claude-opus-4-5-20251101

**Findings Addressed:**
1. ✅ HIGH: Story tasks marked complete (was showing [ ] but implementation existed)
2. ✅ MEDIUM: Dev Agent Record populated (was TBD)
3. ℹ️ MEDIUM: CTA button uses English - documented as intentional (consistent with existing English UI)
4. ℹ️ LOW: Bundle size 563KB - noted for future code splitting
5. ℹ️ LOW: E2E tests deferred to Story 2.6

**Test Results:**
- 163 tests passing
- 0 ESLint errors (1 expected warning for React Hook Form)
- Build successful
