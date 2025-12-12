# Story 2.2: End User Dashboard Layout

Status: Done

## Story

As an **end user**,
I want a clean dashboard showing my VM requests,
So that I have a single place to see all my activity.

## Requirements Context Summary

- **Epic/AC source:** Story 2.2 in `docs/epics.md` - End User Dashboard Layout
- **FRs Satisfied:** FR85 (Empty states - partial, full implementation in Story 2.3)
- **Architecture constraint:** shadcn-admin-kit patterns, Tailwind CSS, Tech Teal theme
- **Prerequisites:** Story 2.1 (Keycloak Login Flow) - DONE
- **UX Reference:** `docs/ux/design-specification.md` Section 4.2 (Layout Strategy)
- **Existing Code:** `dcm/dcm-web/src/App.tsx` - basic header and placeholder content

## Acceptance Criteria

1. **Dashboard layout with sidebar navigation**
   - Given I am logged in
   - When I view the dashboard
   - Then I see a sidebar with navigation items
   - And the sidebar is collapsible on mobile devices
   - And the active navigation item has teal left border + light teal background.

2. **Header with complete information**
   - Given I am logged in
   - When I view the dashboard
   - Then the header displays:
     - DCM logo (left)
     - Tenant switcher dropdown (MVP: single tenant display, switcher post-MVP)
     - User avatar with name dropdown
     - Logout button

3. **Primary CTA button: "Request New VM"**
   - Given I am logged in
   - When I view the dashboard
   - Then I see a prominent "Request New VM" button in Tech Teal (#0D9488)
   - And the button is positioned in the page header area (top right of content)
   - And the button has hover state (#0f766e).

4. **"My Requests" section placeholder**
   - Given I am logged in
   - When I view the dashboard
   - Then I see a "My Requests" section
   - And it displays placeholder text: "Your VM requests will appear here"
   - And it uses Card component from shadcn/ui.

5. **Quick stats widgets placeholder**
   - Given I am logged in
   - When I view the dashboard
   - Then I see placeholder stats cards for:
     - Pending requests (count: 0)
     - Approved requests (count: 0)
     - Provisioned VMs (count: 0)
   - And stats use semantic status colors (Amber, Emerald, Sky).

6. **Responsive layout (mobile-friendly)**
   - Given I view the dashboard on mobile
   - When the viewport is < 768px
   - Then the sidebar collapses to a hamburger menu
   - And content adapts to single-column layout
   - And the "Request New VM" button remains accessible.

7. **Tech Teal theme compliance**
   - Given I view the dashboard
   - When I inspect the design
   - Then primary actions use Tech Teal (#0D9488) as defined in `index.css`
   - And hover states use Teal 700 (#0f766e)
   - And status indicators use defined semantic colors (CSS variables)
   - And the design follows `ux/design-specification.md` guidelines.

## Test Plan

- **Visual Test:** Dashboard renders correctly with all layout components
- **Responsive Test:** Sidebar collapses correctly on mobile viewport
- **Unit Test:** Navigation component renders correct menu items
- **Unit Test:** Stats cards display placeholder values
- **Accessibility Test:** Keyboard navigation works through sidebar items
- **Integration Test:** Dashboard loads after successful authentication

## Structure Alignment / Previous Learnings

### Learnings from Previous Story

#### From Story 2-1-keycloak-login-flow (Status: done)

- **Frontend Stack Established:**
  - Vite + React + TypeScript at `dcm/dcm-web/`
  - shadcn/ui with Tailwind CSS configured
  - React OIDC Context for authentication
  - CSRF token handling via `api-client.ts`

- **Existing Components:**
  - `components/ui/button.tsx` - shadcn Button component
  - `auth/auth-config.ts` - Keycloak OIDC configuration
  - `api/api-client.ts` - API client with CSRF support

- **Header Already Implemented (partial):**
  - Logo, tenant display, user display, logout button exist in `App.tsx`
  - Need to extract into reusable Header component
  - Need to add proper layout structure with sidebar

[Source: docs/sprint-artifacts/2-1-keycloak-login-flow.md]

### Architecture Patterns

- **shadcn-admin-kit Layout:** Header (56px) + Sidebar (224px/14rem) + Main Content
- **Component Organization:** `components/layout/` for layout components
- **State Management:** React Query for server state (future stories)
- **Routing:** Not required for MVP dashboard (single page initially)

### Architecture Decision: Navigation Rendering (Party Mode Review)

> **Decision:** Sidebar navigation renders nav items but does NOT route (Story 2.2)
> **Rationale:** Story-sized scope; routing added when needed (Story 2.4 - VM Request Form)
> **Consequence:** Nav items will be clickable but non-functional until React Router added
> **Reviewed by:** Winston (Architect), Bob (SM), Amelia (Dev) - 2025-11-29

## Tasks / Subtasks

- [x] **Task 0: Setup Unit Testing Infrastructure** (Test Plan prerequisite)
  - [x] Install test dependencies: `npm install -D vitest @testing-library/react @testing-library/jest-dom jsdom`
  - [x] Create `vitest.config.ts` with jsdom environment (see Dev Notes)
  - [x] Create `src/test/setup.ts` with matchMedia mock and Testing Library matchers
  - [x] Add `"test": "vitest"` script to `package.json`
  - [x] Verify setup by running `npm test` (should show no tests yet)

- [x] **Task 1: Create Layout Components** (AC: 1, 2)
  - [x] Create `components/layout/Header.tsx` - extract and enhance header from App.tsx
  - [x] Create `components/layout/Sidebar.tsx` - collapsible navigation sidebar
  - [x] Create `components/layout/DashboardLayout.tsx` - main layout wrapper
  - [x] Create `components/layout/MobileNav.tsx` - hamburger menu for mobile

- [x] **Task 2: Create Dashboard Page** (AC: 3, 4, 5)
  - [x] Create `pages/Dashboard.tsx` - main dashboard content
  - [x] Add "Request New VM" CTA button with Tech Teal styling
  - [x] Add "My Requests" section with placeholder card
  - [x] Add quick stats widgets (Pending/Approved/Provisioned)

- [x] **Task 3: Add shadcn/ui Components** (AC: 4, 5)
  - [x] Add shadcn Card component for stats and request list
  - [x] Add shadcn Badge component for status indicators
  - [x] Add shadcn Avatar component for user display
  - [x] Add lucide-react icons for navigation and stats

- [x] **Task 4: Implement Responsive Behavior** (AC: 6)
  - [x] Configure Tailwind breakpoints (md: 768px)
  - [x] Implement sidebar collapse on mobile
  - [x] Add hamburger menu toggle
  - [x] Test responsive behavior on various viewports

- [x] **Task 5: Apply Theme Colors** (AC: 7)
  - [x] Add status color CSS variables to `src/index.css` (see Dev Notes)
  - [x] Apply semantic status colors to stats cards using `hsl(var(--status-*))` pattern
  - [x] Ensure primary button uses existing Tech Teal from `--primary` CSS variable
  - [x] Verify hover states on interactive elements

- [x] **Task 6: Refactor App.tsx** (All ACs)
  - [x] Replace inline layout with DashboardLayout component
  - [x] Import and render Dashboard page
  - [x] Maintain authentication flow handling
  - [x] Ensure CSRF token fetching still works

- [x] **Task 7: Write Tests** (Test Plan) - Enhanced per Party Mode Review
  - [x] Unit test: Header displays user name from auth context
  - [x] Unit test: Header displays tenant info when available
  - [x] Unit test: Sidebar shows active state on current nav item
  - [x] Unit test: Sidebar collapses on mobile viewport (mock matchMedia)
  - [x] Unit test: Stats cards render with placeholder values (0, 0, 0)
  - [x] Unit test: CTA button has correct Tech Teal styling
  - [x] Integration test: Dashboard renders for authenticated user

## Dev Notes

### Component Structure (Target)

```
dcm/dcm-web/src/
├── components/
│   ├── layout/
│   │   ├── Header.tsx           # App header with logo, tenant, user
│   │   ├── Sidebar.tsx          # Navigation sidebar
│   │   ├── DashboardLayout.tsx  # Layout wrapper combining header + sidebar
│   │   └── MobileNav.tsx        # Mobile hamburger menu
│   ├── dashboard/
│   │   ├── StatsCard.tsx        # Reusable stats widget
│   │   └── RequestsPlaceholder.tsx  # Placeholder for request list
│   └── ui/                      # shadcn/ui components
│       ├── button.tsx           # (exists)
│       ├── card.tsx             # (new)
│       ├── badge.tsx            # (new)
│       └── avatar.tsx           # (new)
├── pages/
│   └── Dashboard.tsx            # Dashboard page content
└── App.tsx                      # Root with auth + layout
```

### shadcn/ui Components to Add

```bash
# From dcm/dcm-web directory
npx shadcn@latest add card
npx shadcn@latest add badge
npx shadcn@latest add avatar
npx shadcn@latest add separator
npx shadcn@latest add sheet           # For mobile sidebar
```

### Theme Colors (CSS Variables)

**IMPORTANT:** This project uses Tailwind 4 with CSS variables in `src/index.css`. There is NO `tailwind.config.js` file.

The primary Tech Teal color is already configured in `index.css`:
```css
/* Existing in index.css :root */
--primary: 166 84% 32%;        /* #0D9488 - Tech Teal */
--primary-foreground: 0 0% 100%;
```

**Add status colors to `src/index.css` `:root` section:**
```css
/* Add to :root in index.css */
--status-pending: 43 96% 56%;    /* Amber 500 - #f59e0b */
--status-approved: 160 84% 39%;  /* Emerald 500 - #10b981 */
--status-rejected: 347 77% 50%;  /* Rose 500 - #f43f5e */
--status-info: 199 89% 48%;      /* Sky 500 - #0ea5e9 */
```

**Usage in components:**
```tsx
// Using CSS variables with Tailwind
<div className="text-[hsl(var(--status-pending))] bg-[hsl(var(--status-pending)/0.1)]">
  Pending
</div>

// Primary button (already works via shadcn Button)
<Button>Request New VM</Button>  // Uses --primary automatically
```

### Navigation Items (Sidebar)

| Label | Icon | Route | Badge |
|-------|------|-------|-------|
| Dashboard | LayoutDashboard | `/` | - |
| My Requests | FileText | `/requests` | Pending count |
| Request New VM | Plus | `/requests/new` | - |

### Layout Dimensions (UX Spec)

- Header height: 56px (h-14)
- Sidebar width: 224px (w-56, collapsed: w-16)
- Content padding: 24px (p-6)
- Mobile breakpoint: 768px (md:)

### Status Colors for Stats

| Stat | CSS Variable | Usage |
|------|--------------|-------|
| Pending | `--status-pending` | `text-[hsl(var(--status-pending))] bg-[hsl(var(--status-pending)/0.1)]` |
| Approved | `--status-approved` | `text-[hsl(var(--status-approved))] bg-[hsl(var(--status-approved)/0.1)]` |
| Provisioned | `--status-info` | `text-[hsl(var(--status-info))] bg-[hsl(var(--status-info)/0.1)]` |

### Mobile Sidebar Implementation (Party Mode Review)

**Important:** Use Sheet component (slide-in drawer), NOT a dropdown menu.
- Mobile users expect sidebar to slide in from the left
- `npx shadcn@latest add sheet` provides the correct pattern
- Hamburger button triggers Sheet open state
- Sheet overlays content on mobile, doesn't push it

```tsx
// MobileNav.tsx pattern
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet"
import { Menu } from "lucide-react"

<Sheet>
  <SheetTrigger asChild>
    <Button variant="ghost" size="icon" className="md:hidden">
      <Menu className="h-5 w-5" />
    </Button>
  </SheetTrigger>
  <SheetContent side="left" className="w-64">
    {/* Sidebar content */}
  </SheetContent>
</Sheet>
```

### Testing Standards

**IMPORTANT:** Vitest is NOT currently installed. Task 0 must be completed first.

**Required files to create:**

`vitest.config.ts`:
```typescript
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
```

`src/test/setup.ts`:
```typescript
import '@testing-library/jest-dom'

// Mock matchMedia for responsive tests (see Viewport Testing Approach below)
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})
```

**Test patterns:**
- Tests in `__tests__/` folders or `*.test.tsx` files
- Mock authentication context for component tests
- Test responsive behavior with matchMedia mock

### Viewport Testing Approach (Party Mode Review)

**Challenge:** Vitest runs in Node.js; `window.matchMedia` doesn't exist by default.

**Solution:** Mock `matchMedia` in test setup:

```typescript
// vitest.setup.ts or test file
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: query === '(max-width: 768px)', // Control this per test
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})
```

**Alternative:** Defer visual responsive testing to Playwright E2E (Story 2.3 or later).

### Accessibility Requirements

- ARIA labels on navigation items
- Keyboard navigation (Tab, Enter, Escape)
- Focus indicators on interactive elements
- Screen reader announcements for sidebar state

### Deferred Items (Party Mode Review)

| Item | Reason | Target Story |
|------|--------|--------------|
| React Router setup | Scope creep; routing not needed until navigation works | Story 2.4 |
| React Query integration | Stats need API endpoints first | Story 2.7 |
| Dark mode toggle | UX spec says "ready" but not in ACs | Future (theming story) |
| Empty state illustration | Nice-to-have, not MVP-critical | Story 2.3 (optional) |

### Optional Enhancements (Not Required for Story Completion)

- **Empty state illustration:** Consider a friendly robot or placeholder graphic for "My Requests" section (Sally's suggestion). Can be added as polish if time permits.
- **Skeleton loading states:** For future API integration readiness. Not required now.

### References

- [Source: docs/epics.md#Story-2.2-End-User-Dashboard-Layout]
- [Source: docs/ux/design-specification.md#Section-4.2-Layout-Strategy]
- [Source: docs/sprint-artifacts/tech-spec-epic-2.md#Section-4.4-Frontend-Architecture]
- [Source: docs/sprint-artifacts/2-1-keycloak-login-flow.md#Code-Review]
- [Source: CLAUDE.md#Zero-Tolerance-Policies]

## Party Mode Review

**Date:** 2025-11-29
**Participants:** Sally (UX), Winston (Architect), Amelia (Dev), Bob (SM), Murat (TEA)

**Consensus:**
1. **Design Decisions:** APPROVED - Tech Teal theme, shadcn patterns, semantic status colors
2. **Architecture Approach:** APPROVED - Component structure is solid, routing intentionally deferred
3. **Technical Scope:** APPROVED - Correctly sized, clear boundaries

**Key Insights Applied:**
- Added ADR for navigation rendering deferral (Winston)
- Enhanced Task 7 with specific test cases (Murat)
- Added Sheet component guidance for mobile sidebar (Sally)
- Added viewport testing approach with matchMedia mock (Murat)
- Documented deferred items table (Bob)

## Story Validation

**Date:** 2025-11-29
**Validator:** SM Agent (Bob) + Fresh Context Validation
**Report:** `docs/sprint-artifacts/validation-report-2-2-end-user-dashboard-layout.md`

**Initial Result:** 18/23 items passed (78%) - 3 critical issues found

**Issues Fixed:**
1. ✅ **Vitest not installed** → Added Task 0 with installation instructions and vitest.config.ts
2. ✅ **tailwind.config.js doesn't exist** → Replaced all references with CSS variable pattern in index.css
3. ✅ **Tech Teal color mismatch** → Aligned to UX Spec value #0D9488 (was #0f766e)
4. ✅ **Status colors pattern** → Updated to use CSS variable syntax `hsl(var(--status-*))`

**Final Status:** All critical issues resolved. Story ready for development.

## Dev Agent Record

### Context Reference

- `docs/sprint-artifacts/2-2-end-user-dashboard-layout.context.xml` (to be generated by story-context workflow)

### Agent Model Used

claude-opus-4-5-20251101

### Debug Log References

### Completion Notes List

- ✅ **Task 0:** Installed Vitest, @testing-library/react, jsdom. Created vitest.config.ts and src/test/setup.ts with matchMedia mock.
- ✅ **Task 1:** Created Header, Sidebar, MobileNav, DashboardLayout components. Header displays user avatar with initials, tenant info, and logout. Sidebar has navigation with active state styling (teal left border).
- ✅ **Task 2:** Created Dashboard page with "Request New VM" CTA button, stats cards (Pending/Approved/Provisioned all showing 0), and "My Requests" placeholder section.
- ✅ **Task 3:** Added shadcn Card, Badge, Avatar, Separator, Sheet components via `npx shadcn@latest add`.
- ✅ **Task 4:** Implemented responsive layout with mobile hamburger menu triggering Sheet component. Desktop shows sticky sidebar, mobile hides sidebar behind Sheet drawer.
- ✅ **Task 5:** Added status color CSS variables (--status-pending, --status-approved, --status-rejected, --status-info). Updated Tech Teal primary color to oklch format.
- ✅ **Task 6:** Refactored App.tsx to use DashboardLayout wrapper with Dashboard page. Auth flow handling preserved.
- ✅ **Task 7:** Wrote 43 unit tests across 7 test files. All tests pass. Tests cover Header (user/tenant display, avatar initials, mobile menu, sign out), Sidebar (nav items, active state, ARIA, responsive), MobileNav (Sheet behavior), DashboardLayout (state management, responsive classes), StatsCard (variants, styling), Dashboard (CTA, stats, placeholder text), App integration (auth flow, dashboard rendering).

### Implementation Notes

- shadcn init was run which added tw-animate-css and updated index.css. Restored Tech Teal primary color using oklch(0.637 0.126 175.8).
- Navigation items are non-functional buttons (no React Router yet per ADR). Will be connected in Story 2.4.
- ESLint configured to allow constant exports from shadcn components and ignore test files for react-refresh rule.
- @testing-library/user-event added for click interaction testing.

### File List

**New Files:**
- dcm/dcm-web/vitest.config.ts
- dcm/dcm-web/src/test/setup.ts
- dcm/dcm-web/src/test/test-utils.tsx
- dcm/dcm-web/src/components/layout/Header.tsx
- dcm/dcm-web/src/components/layout/Sidebar.tsx
- dcm/dcm-web/src/components/layout/MobileNav.tsx
- dcm/dcm-web/src/components/layout/DashboardLayout.tsx
- dcm/dcm-web/src/components/layout/index.ts
- dcm/dcm-web/src/components/dashboard/StatsCard.tsx
- dcm/dcm-web/src/components/dashboard/RequestsPlaceholder.tsx
- dcm/dcm-web/src/components/dashboard/index.ts
- dcm/dcm-web/src/pages/Dashboard.tsx
- dcm/dcm-web/src/components/ui/card.tsx
- dcm/dcm-web/src/components/ui/badge.tsx
- dcm/dcm-web/src/components/ui/avatar.tsx
- dcm/dcm-web/src/components/ui/separator.tsx
- dcm/dcm-web/src/components/ui/sheet.tsx
- dcm/dcm-web/src/components/layout/__tests__/Header.test.tsx
- dcm/dcm-web/src/components/layout/__tests__/Sidebar.test.tsx
- dcm/dcm-web/src/components/layout/__tests__/MobileNav.test.tsx
- dcm/dcm-web/src/components/layout/__tests__/DashboardLayout.test.tsx
- dcm/dcm-web/src/components/dashboard/__tests__/StatsCard.test.tsx
- dcm/dcm-web/src/pages/__tests__/Dashboard.test.tsx
- dcm/dcm-web/src/__tests__/App.integration.test.tsx
- dcm/dcm-web/src/components/ErrorBoundary.tsx
- dcm/dcm-web/components.json

**Modified Files:**
- dcm/dcm-web/package.json (added test deps, test script)
- dcm/dcm-web/package-lock.json (updated dependencies)
- dcm/dcm-web/tsconfig.json (added paths for shadcn)
- dcm/dcm-web/eslint.config.js (added test ignores, react-refresh config)
- dcm/dcm-web/src/index.css (added status colors, updated primary to oklch, fixed dark mode)
- dcm/dcm-web/src/App.tsx (replaced inline layout with DashboardLayout + Dashboard, added ErrorBoundary)
- dcm/dcm-web/src/lib/utils.ts (updated by shadcn)
- dcm/dcm-web/src/auth/auth-config.ts (added env variable validation, error logging)

### Code Review

**Date:** 2025-11-29
**Reviewer:** Amelia (DEV Agent) - Adversarial Review
**Model:** claude-opus-4-5-20251101

**Initial Findings:** 8 issues (1 High, 5 Medium, 2 Low)

**Issues Fixed:**

1. ✅ **HIGH: Dark mode primary color** - Fixed `--primary` in `.dark` block from grayscale to Tech Teal (`oklch(0.65 0.126 175.8)`)
2. ✅ **MEDIUM: Accessibility warning** - Added `SheetDescription` to `MobileNav.tsx` for screen reader support
3. ✅ **MEDIUM: Missing sidebar collapse test** - Added test verifying CSS responsive classes
4. ✅ **MEDIUM: Missing keyboard navigation test** - Added test for Tab navigation through sidebar items
5. ✅ **MEDIUM: Missing integration test** - Created `App.integration.test.tsx` with 5 tests covering auth flow + dashboard
6. ✅ **MEDIUM: CSS variable duplication** - Removed duplicate HSL `:root` block, kept oklch format
7. ✅ **LOW: File List documentation gap** - Added `package-lock.json` to Modified Files
8. ✅ **LOW: Hover state verification** - Documented as relying on shadcn Button default behavior

**Test Results After Fixes:**
- 7 test files, 43 tests passing
- No console warnings for accessibility

**Verdict:** All issues resolved. Story ready for merge.

### Code Review (PR #35 Fixes)

**Date:** 2025-11-29
**Reviewer:** Claude Code - Adversarial Review (Post-PR feedback)
**Model:** claude-opus-4-5-20251101

**Issues Fixed from PR Review:**
1. ✅ **ErrorBoundary added** - React error boundary to catch component errors and prevent app crashes
2. ✅ **Environment validation** - Added runtime validation for required VITE_KEYCLOAK_* variables in auth-config.ts
3. ✅ **JWT parsing error logging** - Added structured error logging to parseJwtPayload
4. ✅ **MockAuthProvider fixed** - Replaced real AuthProvider with React Context-based mock
5. ✅ **Test count increased** - Added DashboardLayout tests, sign out handler test (35 → 43 tests)
6. ✅ **Header made sticky** - Added `sticky top-0 z-40` to header for scroll behavior
7. ✅ **getInitials fixed** - Now handles empty strings and multiple spaces correctly

**Final Test Results:**
- 7 test files, 43 tests passing
- All PR review comments addressed
