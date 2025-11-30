# DVMM UX Design Specification

_Created on 2025-11-24 by Wall-E_
_Generated using BMad Method - Create UX Design Workflow v1.0_

---

## Executive Summary

**Project:** DVMM (Dynamic Virtual Machine Manager)
**Vision:** Self-Service VM-Provisioning that empowers end-users with autonomy and transparency while freeing IT-Admins from email chaos.

**Target Users:**
| Role | Primary Activity | Emotional Goal |
|------|------------------|----------------|
| **End User** | Request VMs, monitor status | "Ich kann das selbst und bin immer informiert" |
| **IT Admin** | Approve/reject requests, oversight | "Keine Email-Flut mehr!" |
| **IT Manager** | Compliance, reporting, configuration | "Ich habe alles im Griff und bin audit-ready" |

**Core Experience:** Request → Approve → Provision → Notify
**Platform:** Web Application (Desktop-first, Mobile-responsive)
**Tech Stack:** React + shadcn-admin-kit (Tailwind-based), Keycloak OIDC, Kotlin/Spring Boot

**UX Inspiration:**
- AWS Cloudscape: Resource management patterns, information density, tenant switcher (header dropdown)
- ServiceNow: Request/Approval workflow structure, status tracking (**Workflow power only, not the complex UI**)
- Azure Portal: Clean hierarchy, Fluent design principles, accessibility

**Design Mantra:** "ServiceNow Workflow Power, Consumer App Simplicity"

**UX Complexity:** Medium-High (3 user roles, 4+ critical journeys, multi-tenant)

**Multi-Tenant UX Pattern:** Header dropdown for tenant switching (AWS-style) - allows quick switching without page reload

---

## 1. Design System Foundation

### 1.1 Design System Choice

**Selected:** shadcn-admin-kit (shadcn/ui + Tailwind CSS)

**Rationale:**
- **Tailwind-based:** Utility-first CSS, highly customizable, consistent with modern React patterns
- **Copy-paste components:** Full control over components, no heavy dependencies
- **Clean aesthetic:** Aligns with "Consumer App Simplicity" goal
- **Admin-kit optimized:** Pre-built patterns for dashboards, tables, forms, charts
- **Accessibility:** Built-in ARIA support, keyboard navigation
- **Dark mode ready:** Theme switching built into the system

**What shadcn-admin-kit provides:**
- Layout components (sidebar, header, content areas)
- Data tables with sorting, filtering, pagination
- Form components with validation states
- Charts and data visualization (Recharts integration)
- Dialog/Modal patterns
- Toast notifications
- Command palette (⌘K)

**Custom components needed for DVMM:**
| Component | Purpose | shadcn Base |
|-----------|---------|-------------|
| VM Request Card | Display VM request with status | Card + Badge |
| Approval Action Bar | Quick approve/reject buttons | Button Group |
| Tenant Switcher | Header dropdown for tenant selection | Select/Dropdown |
| Status Timeline | Show request history | Custom (inspired by Timeline) |
| Resource Quota Indicator | Show remaining quota | Progress + Tooltip |
| VM Size Selector | Visual size selection (S/M/L/XL) | Radio Group + Cards |

**Design System Resources:**
- [shadcn/ui Documentation](https://ui.shadcn.com/)
- [shadcn-admin-kit](https://github.com/shadcn-ui/ui) (or specific admin kit variant)
- Tailwind CSS for customization

---

## 2. Core User Experience

### 2.1 Defining Experience

**The ONE thing that must be effortless:**
- For End Users: Requesting a VM and knowing its status at all times
- For Admins: Seeing all pending requests and approving with one click

**Core Interaction Pattern:**
```
User Request → Approval Queue → Admin Decision → VM Provisioned → Notification
     ↓              ↓                ↓                 ↓              ↓
  Form UI     Dashboard View    One-Click Action   VMware API    Status Update
```

**Experience Principles:**
- **Speed:** Key actions feel instant (< 500ms feedback)
- **Transparency:** Status always visible, no black boxes
- **Simplicity:** AWS-level power with consumer-app simplicity
- **Trust:** Enterprise-grade reliability, clear audit trail

### 2.2 Novel UX Patterns

No novel patterns required - DVMM uses established patterns:
- Request/Approval Workflow (ServiceNow pattern)
- Resource Dashboard (AWS/Azure pattern)
- Status Tracking (standard pattern)
- Multi-tenant Switcher (SaaS pattern)

---

## 3. Visual Foundation

### 3.1 Color System

**Selected Theme:** Tech Teal - Modern, Innovative, Trustworthy

**Decision Rationale (Party Mode Consensus):**
- Differentiates from blue-dominated competition (ServiceNow, Azure, VMware)
- Signals innovation while maintaining enterprise trust
- Excellent accessibility (WCAG AAA compliant)
- Strong semantic color separation for status indicators
- Calming tone aligns with "keine Email-Flut mehr!" emotional goal

#### Primary Colors

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| **Primary** | Teal 700 | `#0f766e` | Main actions, links, focus states |
| **Primary Hover** | Teal 800 | `#115e59` | Button hover, active states |
| **Primary Light** | Teal 100 | `#ccfbf1` | Backgrounds, highlights |
| **Primary Dark** | Teal 900 | `#134e4a` | Text on light backgrounds |

#### Semantic Colors (Status System)

| Status | Color | Hex | Usage |
|--------|-------|-----|-------|
| **Pending** | Amber 500 | `#f59e0b` | Awaiting approval, in queue |
| **Approved** | Emerald 500 | `#10b981` | Success, approved, provisioned |
| **Rejected** | Rose 500 | `#f43f5e` | Rejected, error, failed |
| **Info** | Sky 500 | `#0ea5e9` | Informational, neutral status |
| **Warning** | Orange 500 | `#f97316` | Attention needed, quota low |

#### Neutral Palette

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| **Background** | Slate 50 | `#f8fafc` | Page background |
| **Surface** | White | `#ffffff` | Cards, modals, panels |
| **Border** | Slate 200 | `#e2e8f0` | Dividers, card borders |
| **Text Primary** | Slate 900 | `#0f172a` | Headlines, primary text |
| **Text Secondary** | Slate 600 | `#475569` | Secondary text, labels |
| **Text Muted** | Slate 400 | `#94a3b8` | Placeholders, disabled |

#### Dark Mode Palette

| Role | Light Mode | Dark Mode |
|------|------------|-----------|
| **Background** | `#f8fafc` | `#0f172a` |
| **Surface** | `#ffffff` | `#1e293b` |
| **Border** | `#e2e8f0` | `#334155` |
| **Text Primary** | `#0f172a` | `#f8fafc` |
| **Text Secondary** | `#475569` | `#94a3b8` |

#### Tailwind Configuration

```javascript
// tailwind.config.js - DVMM Theme Extension
module.exports = {
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#0f766e',
          hover: '#115e59',
          light: '#ccfbf1',
          dark: '#134e4a',
        },
        status: {
          pending: '#f59e0b',
          approved: '#10b981',
          rejected: '#f43f5e',
          info: '#0ea5e9',
          warning: '#f97316',
        },
      },
    },
  },
}
```

### 3.2 Typography

**Font Stack:** System fonts (shadcn default) - optimized for performance

```css
font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont,
             "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
```

| Element | Size | Weight | Line Height |
|---------|------|--------|-------------|
| **H1 (Page Title)** | 30px / 1.875rem | 700 | 1.2 |
| **H2 (Section)** | 24px / 1.5rem | 600 | 1.3 |
| **H3 (Card Title)** | 18px / 1.125rem | 600 | 1.4 |
| **Body** | 14px / 0.875rem | 400 | 1.5 |
| **Small/Caption** | 12px / 0.75rem | 400 | 1.4 |
| **Button** | 14px / 0.875rem | 500 | 1 |

### 3.3 Spacing System

**Base Unit:** 4px (Tailwind default)

| Token | Value | Usage |
|-------|-------|-------|
| `space-1` | 4px | Tight spacing (icon gaps) |
| `space-2` | 8px | Default element spacing |
| `space-3` | 12px | Form field gaps |
| `space-4` | 16px | Card padding, section gaps |
| `space-6` | 24px | Card internal sections |
| `space-8` | 32px | Major section spacing |

### 3.4 Border Radius

| Element | Radius | Tailwind Class |
|---------|--------|----------------|
| **Buttons** | 6px | `rounded-md` |
| **Cards** | 8px | `rounded-lg` |
| **Modals** | 12px | `rounded-xl` |
| **Badges/Pills** | 9999px | `rounded-full` |
| **Inputs** | 6px | `rounded-md` |

### 3.5 Shadows

| Level | Usage | Tailwind Class |
|-------|-------|----------------|
| **sm** | Subtle elevation (cards) | `shadow-sm` |
| **DEFAULT** | Dropdowns, popovers | `shadow` |
| **md** | Modals, dialogs | `shadow-md` |
| **lg** | Floating action buttons | `shadow-lg` |

**Interactive Visualizations:**
- Color Theme Explorer: [ux-color-themes.html](./ux-color-themes.html)

---

## 4. Design Direction

### 4.1 Chosen Design Approach

**Design Philosophy:** Clean, functional enterprise UI with consumer-app simplicity

**Key Screens Designed:**

| Screen | User Role | Primary Purpose |
|--------|-----------|-----------------|
| **End User Dashboard** | End User | View my VMs, track request status, request new VMs |
| **VM Request Form** | End User | Multi-step form with visual size selector and quota indicator |
| **Approval Queue** | IT Admin | One-click approve/reject with expandable details |
| **Manager Dashboard** | IT Manager | KPIs, cost tracking, compliance score, audit trail |

### 4.2 Layout Strategy

**Consistent Layout Pattern (shadcn-admin-kit):**

```
┌─────────────────────────────────────────────────────┐
│ Header: Logo │ Tenant Switcher │ Notifications │ User │
├─────────┬───────────────────────────────────────────┤
│         │                                           │
│ Sidebar │              Main Content                 │
│  (Nav)  │                                           │
│         │                                           │
└─────────┴───────────────────────────────────────────┘
```

**Header (56px):**
- Logo left
- Tenant Switcher dropdown (AWS-style)
- Notification bell with badge
- User avatar with dropdown

**Sidebar (224px / 14rem):**
- Collapsible on mobile
- Active state: teal left border + light teal background
- Icons + text labels
- Badge counts for pending items

**Main Content:**
- Page title + description
- Primary action button (top right)
- Content cards/tables

### 4.3 Information Density

**Guiding Principle:** Show what matters, hide complexity

| Context | Density | Example |
|---------|---------|---------|
| **Dashboard** | Medium | Status cards + VM list with key info |
| **Request Form** | Low | One section at a time, clear progress |
| **Approval Queue** | Medium-High | Collapsed cards, expand for details |
| **Manager Reports** | High | KPIs + charts + tables (data-dense) |

### 4.4 Interaction Patterns

**Primary Actions:**
- Solid teal button (`bg-primary`)
- Clear hover state (`bg-primary-hover`)
- Disabled state with reduced opacity

**Secondary Actions:**
- Outline buttons (`border border-slate-300`)
- Ghost buttons for tertiary actions

**Destructive Actions:**
- Red outline for reject/delete
- Confirmation dialog required

**Cards:**
- Hover: subtle border color change
- Click: expand for details
- Quick actions visible on hover

**Interactive Mockups:**
- Design Direction Showcase: [ux-design-directions.html](./ux-design-directions.html)

### 4.5 Design Review Feedback (Party Mode)

**MVP-Critical Improvements Identified:**

| Issue | Screen | Priority | Resolution |
|-------|--------|----------|------------|
| **Bulk Approval Actions** | Admin Queue | **MVP** | Add checkboxes + "Approve/Reject Selected" buttons (PRD: FR-APPROVAL-003) |
| **Draft Saving** | Request Form | **MVP** | Auto-save form state between steps to prevent data loss |

**Post-MVP Improvements:**

| Issue | Screen | Priority | Resolution |
|-------|--------|----------|------------|
| Multi-Tenant Overview | Manager Dashboard | Post-MVP | Add "All Tenants" tab for managers with multiple tenant responsibility |
| Estimated Approval Time | Request Form | Post-MVP | Add hint: "Typically approved within 2-4 hours" |

**Technical Questions Raised (for Architecture):**

1. **Tenant Switcher:** URL-based (`/tenant/{id}/...`) recommended for bookmarkability
2. **Approval Actions:** Optimistic UI with rollback on error
3. **Real-time Updates:** WebSocket for Approval Queue (polling fallback)

---

## 5. User Journey Flows

### 5.1 Critical User Paths

**Core Workflow (The Product):**
```
End User Request → Admin Approval → VM Provisioned → Notification
```

### 5.2 End User Journey: Request a VM

**Persona:** Developer needing a VM for a project
**Emotional Goal:** "Ich kann das selbst und bin immer informiert"

```
┌─────────────────────────────────────────────────────────────────────┐
│ TRIGGER: User needs a new VM for their project                      │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 1. LOGIN                                                            │
│    • Keycloak SSO (single sign-on from corporate IdP)               │
│    • Land on "My VMs" dashboard                                     │
│    • See existing VMs + their status                                │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. INITIATE REQUEST                                                 │
│    • Click "Request New VM" button (prominent CTA)                  │
│    • Or: Sidebar → "New Request"                                    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. FILL FORM (Multi-Step)                                           │
│    Step 1 - Basics:                                                 │
│      • VM Name (auto-suggestion based on project)                   │
│      • Project selection (dropdown)                                 │
│      • Purpose/Justification (free text)                            │
│    ────────────────────────────────────────────────────             │
│    Step 2 - Resources:                                              │
│      • Size selector: S / M / L / XL (visual cards)                 │
│      • Storage amount (slider or input)                             │
│      • Operating System (Windows/Linux templates)                   │
│      • Quota indicator shows remaining capacity                     │
│    ────────────────────────────────────────────────────             │
│    Step 3 - Review:                                                 │
│      • Summary of all selections                                    │
│      • Estimated monthly cost                                       │
│      • "Submit Request" button                                      │
│                                                                     │
│    [Draft auto-saved between steps]                                 │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. CONFIRMATION                                                     │
│    • Success toast: "Request submitted!"                            │
│    • Redirect to "My Requests" with new request showing "Pending"   │
│    • Email notification sent to user                                │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. WAIT & TRACK                                                     │
│    • Request visible in "My Requests" list                          │
│    • Status badge: Pending → Approved/Rejected                      │
│    • Timeline shows history (submitted, under review, etc.)         │
│    • Push notification when status changes                          │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 6. OUTCOME                                                          │
│    IF Approved:                                                     │
│      • Status → "Provisioning" → "Running"                          │
│      • VM appears in "My VMs" with IP address                       │
│      • Email with connection details                                │
│    IF Rejected:                                                     │
│      • Status → "Rejected" with reason                              │
│      • Option to "Edit & Resubmit"                                  │
└─────────────────────────────────────────────────────────────────────┘

**Happy Path Time:** < 5 minutes (form) + < 4 hours (approval) + < 30 minutes (provisioning)
**Key Metrics:** Form completion rate, time-to-submit, abandonment rate
```

### 5.3 IT Admin Journey: Approve Requests

**Persona:** IT Administrator responsible for VM approvals
**Emotional Goal:** "Keine Email-Flut mehr!"

```
┌─────────────────────────────────────────────────────────────────────┐
│ TRIGGER: Notification badge shows pending requests                  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 1. LOGIN & LANDING                                                  │
│    • Keycloak SSO                                                   │
│    • Land on Admin Dashboard                                        │
│    • Notification bell shows count (e.g., "3")                      │
│    • Sidebar "Approval Queue" has badge                             │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. REVIEW QUEUE                                                     │
│    • Navigate to "Approval Queue"                                   │
│    • See list of pending requests (collapsed cards)                 │
│    • Quick info visible: Name, Requester, Size, Time ago            │
│    • Filter by requester, project, size                             │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. EVALUATE REQUEST                                                 │
│    Option A - Quick Action:                                         │
│      • Click ✓ (Approve) or ✗ (Reject) directly on card             │
│      • For simple, obviously OK requests                            │
│    ────────────────────────────────────────────────────             │
│    Option B - Detailed Review:                                      │
│      • Click card to expand                                         │
│      • See full justification, specs, cost estimate                 │
│      • Check requester's current quota usage                        │
│      • View requester's history (optional)                          │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. TAKE ACTION                                                      │
│    APPROVE:                                                         │
│      • Click "Approve" button                                       │
│      • Optional: Add comment                                        │
│      • Request moves to "Provisioning"                              │
│    ────────────────────────────────────────────────────             │
│    REJECT:                                                          │
│      • Click "Reject" button                                        │
│      • Required: Provide reason (dropdown + free text)              │
│      • Request marked as "Rejected"                                 │
│    ────────────────────────────────────────────────────             │
│    REQUEST INFO:                                                    │
│      • Click "Request Info" button                                  │
│      • Add question for requester                                   │
│      • Status → "Needs Information"                                 │
│    ────────────────────────────────────────────────────             │
│    BULK ACTION (MVP):                                               │
│      • Select multiple requests via checkboxes                      │
│      • Click "Approve Selected" or "Reject Selected"                │
│      • Confirmation dialog with count                               │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. CONFIRMATION & NEXT                                              │
│    • Toast: "Request approved" / "Request rejected"                 │
│    • Card removed from queue (optimistic UI)                        │
│    • Badge count decrements                                         │
│    • Continue with next request or return to dashboard              │
└─────────────────────────────────────────────────────────────────────┘

**Happy Path Time:** < 30 seconds per simple approval, < 2 minutes for detailed review
**Key Metrics:** Avg. approval time, approval rate, bulk action usage
```

### 5.4 IT Manager Journey: Oversight & Compliance

**Persona:** IT Manager responsible for governance and budgets
**Emotional Goal:** "Ich habe alles im Griff und bin audit-ready"

```
┌─────────────────────────────────────────────────────────────────────┐
│ TRIGGER: Weekly review / Audit preparation / Budget meeting         │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 1. LOGIN & OVERVIEW                                                 │
│    • Keycloak SSO                                                   │
│    • Land on Manager Dashboard (Analytics view)                     │
│    • Instant KPI visibility: VMs, Cost, Approval Time, Compliance   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. REVIEW METRICS                                                   │
│    • KPI cards with trend indicators (+/-%)                         │
│    • Resource usage chart (7-day/30-day/90-day)                     │
│    • Cost breakdown by project (bar chart)                          │
│    • Compliance score (percentage)                                  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. DRILL DOWN (as needed)                                           │
│    • Click on metric to see details                                 │
│    • Filter by date range, project, user                            │
│    • Navigate to specific reports:                                  │
│      - Compliance → Policy violations, security events              │
│      - Cost Center → Budget vs. actual, forecast                    │
│      - Audit Log → Full activity history                            │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. EXPORT & SHARE                                                   │
│    • Click "Export Report" button                                   │
│    • Select format: PDF, Excel, CSV                                 │
│    • Select date range and filters                                  │
│    • Download or email to stakeholders                              │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. CONFIGURE (Settings)                                             │
│    • Set quotas per project/user                                    │
│    • Configure approval policies                                    │
│    • Manage VM templates                                            │
│    • Set up alerts (quota thresholds, anomalies)                    │
└─────────────────────────────────────────────────────────────────────┘

**Happy Path Time:** < 5 minutes for weekly overview, < 15 minutes for detailed audit prep
**Key Metrics:** Dashboard visit frequency, export count, alert configuration rate
```

### 5.5 Error States & Edge Cases

| Scenario | User Sees | Recovery Path |
|----------|-----------|---------------|
| **Quota Exceeded** | Warning during request form | Adjust VM size or request quota increase |
| **Session Timeout** | Login redirect, draft preserved | Re-login, continue from saved draft |
| **Approval Timeout** | Request auto-escalated | Notification to backup approver |
| **Provisioning Failed** | Status "Failed" with error | Admin notified, can retry or investigate |
| **Provisioning Timeout** | Status "Failed - Timeout" after 60 min | Admin can Retry or Investigate |
| **Network Error** | Toast with retry option | Automatic retry, manual retry button |
| **Request Rejected** | Status "Rejected" with reason | User can "Edit & Resubmit" |

**Provisioning Timeout Handling (Critical):**
```
Admin Approves → Status: "Provisioning" (with timestamp)
                        ↓
         Frontend shows: "Provisioning since X minutes..."
                        ↓
         [60 min timeout] → Status: "Failed - Provisioning Timeout"
                        ↓
         Admin Options: [Retry Provisioning] [Investigate] [Cancel Request]
```

**Edit & Resubmit Flow (Rejected Requests):**
```
Request Rejected → User sees reason
                        ↓
         User clicks "Edit & Resubmit"
                        ↓
         Form pre-filled with original values
                        ↓
         User modifies based on rejection reason
                        ↓
         Submit → New request created (linked to original)
                        ↓
         Back in Approval Queue
```

### 5.6 Notification Strategy

**MVP Scope (Email + Badge only):**

| Event | End User | IT Admin | IT Manager |
|-------|----------|----------|------------|
| Request Submitted | ✅ Email | ✅ In-App Badge | - |
| Request Approved | ✅ Email | - | - |
| Request Rejected | ✅ Email | - | - |
| VM Provisioned | ✅ Email | - | - |
| Provisioning Failed | ✅ Email | ✅ Email | - |

**Post-MVP Enhancements:**

| Event | End User | IT Admin | IT Manager |
|-------|----------|----------|------------|
| Request Submitted | + Push | + Push | - |
| Request Approved/Rejected | + Push | - | - |
| Quota Warning (80%) | + In-App | - | + Email |
| Quota Exceeded | + In-App | + In-App | + Email |
| Compliance Alert | - | + In-App | + Email + In-App |

### 5.7 Technical Decisions (MVP)

| Decision | MVP Choice | Rationale | Post-MVP |
|----------|------------|-----------|----------|
| **Draft Saving** | LocalStorage | Simple, no server changes needed | Server-side sync for cross-device |
| **Real-time Badge** | 30-second Polling | Avoids WebSocket complexity | WebSocket for instant updates |
| **Notifications** | Email only | Critical path, proven technology | Push + In-App notifications |
| **Optimistic UI** | Yes, with rollback | Better perceived performance | - |

**LocalStorage Draft Schema:**
```json
{
  "dvmm_draft_request": {
    "step": 2,
    "data": {
      "name": "dev-analytics-01",
      "project": "data-analytics",
      "size": "L",
      "storage": 500
    },
    "savedAt": "2025-11-24T14:30:00Z"
  }
}
```

---

## 6. Component Library

### 6.1 Component Strategy

**Base:** shadcn-admin-kit components (copy-paste, full control)
**Customization:** Tailwind theme extension for DVMM colors

### 6.1.1 React Coding Standards

**React Compiler Optimization:**
- Project uses React Compiler for automatic memoization
- **PROHIBITED:** `useMemo`, `useCallback`, `React.memo` (ESLint enforced)
- Write straightforward code; compiler handles optimization

**Component Patterns:**
- Function components only (no class components)
- TypeScript interfaces for props
- Named exports for components
- PascalCase for component names, camelCase for hooks

```tsx
// ✅ Correct pattern
interface VMCardProps {
  vm: VirtualMachine
  onSelect: (id: string) => void
}

export function VMCard({ vm, onSelect }: VMCardProps) {
  return <Card onClick={() => onSelect(vm.id)}>...</Card>
}
```

### 6.2 shadcn Components Used

| Component | shadcn Name | DVMM Usage |
|-----------|-------------|------------|
| Button | `button` | Primary/Secondary/Destructive actions |
| Card | `card` | VM cards, request cards, KPI cards |
| Badge | `badge` | Status indicators (Pending, Running, etc.) |
| Input | `input` | Form fields |
| Select | `select` | Dropdowns (Project, OS template) |
| Textarea | `textarea` | Justification field |
| Dialog | `dialog` | Confirmation modals, reject reason |
| Toast | `toast` | Success/error notifications |
| Progress | `progress` | Quota indicator, provisioning progress |
| Table | `table` | Audit log, VM list (desktop) |
| Tabs | `tabs` | Dashboard sections |
| Avatar | `avatar` | User profile |
| Dropdown Menu | `dropdown-menu` | User menu, tenant switcher |
| Checkbox | `checkbox` | Bulk selection |
| Sidebar | `sidebar` | Navigation (from admin-kit) |
| Command | `command` | ⌘K palette (Post-MVP) |

### 6.3 Custom DVMM Components

| Component | Purpose | Composition |
|-----------|---------|-------------|
| **VMRequestCard** | Display VM request with status | Card + Badge + Button Group |
| **ApprovalActionBar** | Quick approve/reject/info buttons | Button Group (3 variants) |
| **TenantSwitcher** | Header dropdown for tenant selection | Dropdown Menu + Avatar |
| **StatusTimeline** | Show request history | Custom (ul + status dots) |
| **QuotaIndicator** | Show remaining quota with warning | Progress + Tooltip + conditional color |
| **VMSizeSelector** | Visual S/M/L/XL selection | Radio Group + Card grid |
| **BulkActionBar** | Floating bar when items selected | Fixed position + Button Group |
| **KPICard** | Metric with trend indicator | Card + number + Badge (trend) |

### 6.4 Component Specifications

**VMRequestCard:**
```
┌────────────────────────────────────────────────────┐
│ [Icon] VM Name                    [Status Badge]   │
│        Specs (4 vCPU, 16GB)       Time ago         │
│        ─────────────────────────────────────────── │
│        (Expandable: Justification, Full Specs)     │
│        [Approve] [Reject] [Request Info]           │
└────────────────────────────────────────────────────┘
```

**VMSizeSelector:**
```
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│  S  │ │  M  │ │  L  │ │ XL  │
│2CPU │ │4CPU │ │8CPU │ │16CPU│
│4GB  │ │8GB  │ │16GB │ │32GB │
└─────┘ └─────┘ └─────┘ └─────┘
  □       ■       □       □     (selected state)
```

**QuotaIndicator:**
```
Your Quota                      75% used
[████████████████░░░░░░]
This request uses 8 of 32 remaining vCPUs
```

**BulkActionBar (floating):**
```
┌──────────────────────────────────────────────────┐
│ 3 items selected   [Approve All] [Reject All] [×]│
└──────────────────────────────────────────────────┘
```

---

## 7. UX Pattern Decisions

### 7.1 Consistency Rules

**Navigation:**
- Sidebar always visible on desktop (collapsible on mobile)
- Active nav item: teal left border + light teal background
- Badge counts on nav items for pending actions

**Actions:**
- Primary action: Top-right of content area
- Destructive actions: Red outline, require confirmation
- Bulk actions: Floating bar when items selected

**Feedback:**
- Success: Green toast, auto-dismiss 3s
- Error: Red toast, manual dismiss required
- Loading: Inline spinner or skeleton

**Status Colors (Consistent everywhere):**
| Status | Color | Background |
|--------|-------|------------|
| Pending | Amber 500 | Amber 100 |
| Approved/Running | Emerald 500 | Emerald 100 |
| Rejected/Failed | Rose 500 | Rose 100 |
| Provisioning | Sky 500 | Sky 100 |
| Stopped | Slate 500 | Slate 100 |

### 7.2 Form Patterns

**Multi-Step Forms:**
- Progress indicator at top (steps with numbers)
- "Continue" button advances, "Back" goes back
- Draft auto-saved to LocalStorage
- Review step before final submit

**Validation:**
- Inline validation on blur
- Error messages below field in red
- Required fields marked with `*`
- Disabled submit until valid

**Field Help:**
- Placeholder text for format hints
- Helper text below field for additional context
- Tooltip (?) for complex fields

### 7.3 Table Patterns

**Data Tables:**
- Sortable columns (click header)
- Filterable via search/dropdowns above table
- Pagination at bottom (25/50/100 per page)
- Row hover highlight
- Row click expands or navigates

**Card Lists (Mobile/Dashboard):**
- Used when < 5 columns needed
- Compact info, expandable for details
- Touch-friendly tap targets (44px min)

### 7.4 Modal Patterns

**Confirmation Dialogs:**
- Title: Action being confirmed
- Body: Consequence explanation
- Buttons: Cancel (left), Confirm (right, colored by action type)

**Form Dialogs:**
- Used for quick edits, reject reason
- Close on backdrop click (if not dirty)
- Warn before closing if unsaved changes

### 7.5 Loading States

| Context | Pattern |
|---------|---------|
| Page load | Full-page skeleton |
| Data fetch | Inline spinner |
| Action submit | Button spinner + disabled |
| Background refresh | Subtle indicator (not blocking) |

### 7.6 Empty States

| Context | Message | Action |
|---------|---------|--------|
| No VMs | "You don't have any VMs yet" | "Request your first VM" button |
| No pending requests | "All caught up!" | - |
| No search results | "No results for 'X'" | "Clear filters" link |

---

## 8. Responsive Design & Accessibility

### 8.1 Responsive Strategy

**Approach:** Desktop-first, mobile-responsive

**Breakpoints (Tailwind defaults):**
| Breakpoint | Width | Target |
|------------|-------|--------|
| `sm` | 640px | Large phones |
| `md` | 768px | Tablets |
| `lg` | 1024px | Small laptops |
| `xl` | 1280px | Desktops |
| `2xl` | 1536px | Large screens |

### 8.2 Layout Adaptations

**Desktop (≥1024px):**
- Full sidebar (224px) + content area
- Multi-column layouts where appropriate
- Tables for data-heavy views

**Tablet (768px - 1023px):**
- Collapsible sidebar (hamburger menu)
- Single or two-column layouts
- Cards instead of tables

**Mobile (<768px):**
- Bottom navigation or hamburger
- Single column, full-width cards
- Simplified forms (one field visible at a time)
- Touch-optimized buttons (min 44px)

### 8.3 Component Responsiveness

| Component | Desktop | Tablet | Mobile |
|-----------|---------|--------|--------|
| Sidebar | Always visible | Collapsible | Hamburger menu |
| VM List | Table view | Card list | Card list (compact) |
| Request Form | Multi-column | Single column | Single column |
| Approval Queue | Expanded cards | Cards | Cards (minimal) |
| Manager Dashboard | 4-col KPIs | 2-col KPIs | Stacked KPIs |

### 8.4 Accessibility (WCAG 2.1 AA)

**Color & Contrast:**
- All text meets 4.5:1 contrast ratio (AA)
- Primary teal (#0f766e) on white = 4.8:1 ✅
- Status colors have sufficient contrast
- Never rely on color alone (use icons + labels)

**Keyboard Navigation:**
- All interactive elements focusable
- Logical tab order
- Visible focus indicators (teal outline)
- Escape closes modals
- Enter/Space activates buttons

**Screen Readers:**
- Semantic HTML (headings, landmarks)
- ARIA labels on icons and buttons
- Live regions for toasts/notifications
- Form labels properly associated

**Motor Accessibility:**
- Minimum touch target: 44x44px
- Adequate spacing between interactive elements
- No time limits on forms (except session timeout)
- Undo available for destructive actions

### 8.5 Accessibility Checklist

| Requirement | Implementation |
|-------------|----------------|
| Alt text for images | All images have descriptive alt text |
| Form labels | Every input has associated label |
| Error identification | Errors announced, linked to field |
| Focus management | Focus moves logically, trapped in modals |
| Skip links | "Skip to main content" link |
| Reduced motion | Respects `prefers-reduced-motion` |
| High contrast | Supports forced-colors mode |

### 8.6 Internationalization (i18n)

**MVP:** German (de-DE) and English (en-US)

**Considerations:**
- RTL support not required (German/English only)
- Date/time: Locale-aware formatting
- Numbers: Locale-aware (1.000,00 vs 1,000.00)
- Currency: EUR with € symbol
- Text expansion: Allow 30% extra space for translations

---

## 9. Implementation Guidance

### 9.1 Completion Summary

**UX Design Specification Status:** ✅ Complete

| Section | Status | Key Decisions |
|---------|--------|---------------|
| Design System | ✅ | shadcn-admin-kit + Tailwind |
| Color Theme | ✅ | Tech Teal (#0f766e) |
| Typography | ✅ | System fonts, 14px base |
| Core Experience | ✅ | Request → Approve → Provision → Notify |
| Design Direction | ✅ | 4 screens designed, Party Mode reviewed |
| User Journeys | ✅ | 3 roles fully mapped |
| Components | ✅ | 8 custom components defined |
| UX Patterns | ✅ | Consistency rules established |
| Accessibility | ✅ | WCAG 2.1 AA compliant |

### 9.2 Implementation Priority

**Phase 1: Core Flow (Tracer Bullet)**
1. Basic layout (Header + Sidebar + Content)
2. End User Dashboard (My VMs)
3. VM Request Form (3-step)
4. Admin Approval Queue (with bulk actions)

**Phase 2: Full MVP**
5. Status tracking + notifications
6. Manager Dashboard (KPIs + reports)
7. Quota management UI
8. Error states + edge cases

**Phase 3: Polish**
9. Dark mode
10. Command palette (⌘K)
11. Advanced filtering
12. Export reports

### 9.3 Developer Handoff Checklist

| Artifact | Location | Status |
|----------|----------|--------|
| UX Specification | `docs/ux-design-specification.md` | ✅ |
| Color Theme Visualizer | `docs/ux-color-themes.html` | ✅ |
| Screen Mockups | `docs/ux-design-directions.html` | ✅ |
| Tailwind Config | Section 3.1 | ✅ |
| Component Specs | Section 6.4 | ✅ |
| User Journeys | Section 5 | ✅ |

### 9.4 Design-Dev Collaboration

**For Developers:**
- Start with shadcn-admin-kit setup
- Apply Tailwind theme extension (Section 3.1)
- Reference mockups in `ux-design-directions.html`
- Follow component specifications in Section 6
- Check consistency rules in Section 7

**Open Questions for Architecture:**
1. Tenant switcher: URL-based vs session-based routing?
2. Real-time updates: WebSocket infrastructure available?
3. File upload for VM templates: S3/MinIO integration?

### 9.5 UX Metrics to Track

| Metric | Target | Measurement |
|--------|--------|-------------|
| Form Completion Rate | > 85% | Analytics |
| Time to Submit Request | < 5 min | Analytics |
| Approval Turnaround | < 4 hours | Backend metrics |
| Admin Efficiency | < 30s per approval | Backend metrics |
| Error Rate | < 2% | Error tracking |
| User Satisfaction (dNPS) | > +50 | Survey (Post-MVP) |

---

## Appendix

### Related Documents

- Product Requirements: `docs/prd.md`
- Product Brief: `docs/product-brief-dvmm-2025-11-24.md`
- Market Research: `docs/research-market-2025-11-24.md`

### Core Interactive Deliverables

This UX Design Specification was created through visual collaboration:

- **Color Theme Visualizer**: docs/ux-color-themes.html
  - Interactive HTML showing all color theme options explored
  - Live UI component examples in each theme
  - Side-by-side comparison and semantic color usage

- **Design Direction Mockups**: docs/ux-design-directions.html
  - Interactive HTML with design approaches
  - Full-screen mockups of key screens
  - Design philosophy and rationale for each direction

### Version History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-11-24 | 1.0 | Initial UX Design Specification | Wall-E |

---

_This UX Design Specification was created through collaborative design facilitation, not template generation. All decisions were made with user input and are documented with rationale._
