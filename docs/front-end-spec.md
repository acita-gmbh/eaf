# Enterprise Application Framework (v0.1) UI/UX Specification

## Introduction

This document defines the user experience goals, information architecture, user flows, and visual design specifications for the Enterprise Application Framework (v0.1)'s user interface. It serves as the foundation for visual design and frontend development, ensuring a cohesive and user-centered experience.

#### Overall UX Goals & Principles

This first section establishes our guiding vision, synthesized from the project brief and PRD.

* **Target User Personas:**
    Our personas are defined in the Project Brief. Our designs must serve:
    1.  **Primary:** Axians Developers (who need speed and clarity, and utilities like the CLI/TUI).
    2.  **Secondary:** Product Managers (need velocity), The Security Team (need compliance/audit UIs), and End Customers (need a stable, modern UX, replacing the outdated DCA interface).
    3.  **Specific UI:** We must also design for the internal React-Admin operator portal.

* **Usability Goals:**
    1.  **Efficiency:** The design must support the <1 month developer productivity goal (Goal #2).
    2.  **Clarity:** Provide intuitive navigation for complex configurations (like Flowable BPMN and Tenancy).
    3.  **Compliance:** The interface must be designed to meet our WCAG Level A standard and support the ASVS security goals.

* **Design Principles:**
    1.  **Utility First:** Prioritize function, data density, and operational speed. This is an enterprise tool, not a marketing site.
    2.  **Clarity Over Cleverness:** Enforce absolute consistency in patterns. Developers must be able to predict how a component works.
    3.  **Progressive Disclosure:** Hide complex configurations until they are needed.
    4.  **Accessible by Default:** Ensure designs meet WCAG Level A standards from the start.

---

## Information Architecture (IA)

#### Site Map / Screen Inventory

This diagram outlines the primary areas of the EAF Operator Portal (React-Admin).

```mermaid
graph TD
    A[EAF Operator Portal] --> B(Dashboard / Health);
    A --> C(Security & Access);
    C --> C1(Tenant Management);
    C --> C2(User Role Mapping);
    
    A --> D(Product Management);
    D --> D1(Licensing Server);
    D1 --> D1a(Manage Products);
    D1b(Manage Licenses) -.-> D1a;
    D1 --> D1b;

    A --> E((Post-MVP: Observability));
    E --> E1(Grafana Dashboards);
    E --> E2(Log Viewer);

    A --> F((Post-MVP: Workflow Engine));
    F --> F1(Flowable Process Definitions);
    F --> F2(Process Instances);
    F --> F3(Dockets Workflow Builder);
````

#### Navigation Structure

* **Primary Navigation:** The main navigation will be a persistent left-hand sidebar, standard for a React-Admin portal. Navigation will be role-based (e.g., an Operator sees different items than a Security Admin).
* **MVP Structure:** The MVP sidebar will include:
    1.  Dashboard (Health)
    2.  Security & Access (Tenancy/Roles)
    3.  Licensing Server (Product/License Management)
* **Post-MVP Structure:** The "Observability" and "Workflow Engine" items will be added to the primary navigation sidebar (as shown in the map) when those Phase 2 features are implemented.
* **Secondary Navigation:** Navigation within modules (like "Licensing Server") will use tabs or sub-menus to switch between related resources (like "Products" and "Licenses").

-----

## User Flows

### User Flow: Admin: Create New Tenant

**User Goal:** As a Security Admin, I need to create and provision a new Tenant in the EAF, so that a new customer can be onboarded and their data remains isolated.

**Entry Points:** Operator Portal (React-Admin) $\\rightarrow$ "Security & Access" Tab $\\rightarrow$ "Tenant Management" Page.

**Success Criteria:** A new Tenant record exists in the EAF, and the corresponding security context (realm/group) is created in Keycloak, allowing new users to be assigned to that tenant.

#### Flow Diagram

```mermaid
graph TD
    A[Start: Admin logs into React-Admin Portal] --> B(Navigate to 'Security & Access');
    B --> C(Click 'Tenant Management');
    C --> D(Click 'Create New Tenant');
    D --> E[Fill Tenant Details Form (Name, ID)];
    E --> F(Submit);
    F --> G{EAF API: Validate Input};
    G -- Valid --> H(Dispatch Axon 'CreateTenantCommand');
    G -- Invalid --> E;
    H --> I(Event: TenantCreated);
    I --> J(Projection: Tenant Read Model Updated);
    I --> K(Saga: Call Keycloak Admin API);
    K --> L(Keycloak: Create Tenant Realm/Group);
    L -- Success --> M[React-Admin: Show Success / Redirect to Tenant List];
    L -- Fail --> N[React-Admin: Show Error (Keycloak Failure)];
    H -- Fail (e.g., ID exists) --> N;
```

#### Edge Cases & Error Handling:

* **Validation:** Tenant ID/Name already exists (must be unique). API returns 409 Conflict.
* **Saga Failure:** The Axon command (H) succeeds, but the Keycloak API call (K) fails. This requires a compensating transaction (a Saga pattern) to roll back the tenant creation or mark it as "Pending Provisioning."
* **Permissions:** The logged-in Admin does not have the "create\_tenant" role; the "Create" button (D) should be hidden or disabled.

### User Flow: Operator: Create New Product (Licensing Server)

**User Goal:** As an EAF Operator, I need to define a new Product (which can later be licensed) using the React-Admin portal.

**Entry Points:** Operator Portal $\\rightarrow$ "Product Management" Tab $\\rightarrow$ "Licensing Server" $\\rightarrow$ "Manage Products" List $\\rightarrow$ Click "Create".

**Success Criteria:** A new "Product" aggregate exists in the event store, and the new Product read model is visible in the React-Admin "Products" list.

#### Flow Diagram

```mermaid
graph TD
    A[Start: Operator in 'Products' List View] --> B(Click 'Create Product' Button);
    B --> C[React-Admin: Show 'Create Product' Form];
    C --> D(Operator fills form: Name, SKU, etc.);
    D --> E[Click Save];
    E -- Client Validation Fail --> C;
    E -- Client Validation OK --> F{RA DataProvider: Send API request};
    F --> G(EAF API: POST /products);
    G --> H(Dispatch Axon 'CreateProductCommand');
    H -- Success --> I(Event: ProductCreated);
    I --> J(Projection: Product Read Model Updated);
    J --> K[React-Admin: Show Success Notification / Redirect to List View];
    H -- Business Logic Fail (e.g., SKU conflicts) --> L[React-Admin: Show Error Notification (e.g., 'SKU already exists')];
```

#### Edge Cases & Error Handling:

* **Client Validation:** The React-Admin form (C) must validate required fields (Name, SKU) before enabling Save (E).
* **API Validation (Conflict):** The Command Handler (H) must reject a command if the SKU already exists. The API (G) must return a 409 Conflict.
* **AuthN/AuthZ:** The request (F) must include the AuthN token (Epic 3). If the operator lacks the "product\_manager" role (or equivalent), the API must return 403 Forbidden.

### User Flow: Operator: Issue New License (Licensing Server)

**User Goal:** As an EAF Operator, I need to issue a new software License for a specific Product (from Flow 2) to a specific Tenant (from Flow 1).

**Entry Points:** Operator Portal (React-Admin) $\\rightarrow$ "Product Management" Tab $\\rightarrow$ "Licensing Server" $\\rightarrow$ "Manage Licenses" List $\\rightarrow$ Click "Issue License".

**Success Criteria:** A new "License" aggregate exists in the event store, correctly associated with both the selected Product ID and the selected Tenant ID.

#### Flow Diagram

```mermaid
graph TD
    A[Start: Operator in 'Licenses' List View] --> B(Click 'Issue License');
    B --> C[React-Admin: Show 'Issue License' Wizard Form];
    C --> D(Step 1: Operator selects Tenant from list);
    D --> E(Step 2: Operator selects Product from list);
    E --> F(Step 3: Operator fills license details (e.g., expiry, user count));
    F --> G[Click Save/Issue];
    G --> H{RA DataProvider: Send API request};
    H --> I(EAF API: POST /licenses);
    I --> J(Dispatch Axon 'IssueLicenseCommand');
    J -- Contains ProductID, TenantID, Details --> K[LicenseAggregate];
    K -- Success --> L(Event: LicenseIssued);
    L --> M(Projection: License Read Model Updated);
    M --> N[React-Admin: Show Success / Redirect to License List];
    K -- Fail (e.g., Invalid TenantID) --> O[React-Admin: Show Error Notification];
```

#### Edge Cases & Error Handling:

* **Coordination Risk:** The selected Tenant ID (from Epic 4) or Product ID (from Flow 2) must be valid. The Command Handler (K) must reject the command if either entity does not exist.
* **Permissions:** Operator must have "license\_issuer" role (validated by Epic 3 security).

-----

## Wireframes & Mockups

#### Design Files

* **Primary Design Files:** None. This UI/UX Specification document serves as the primary low-fidelity specification for conceptual layout and component design.

-----

#### Key Screen Layouts

**Screen:** Tenant Management Portal

* **Purpose:** Allow Security Admins to create, view, edit, and manage system tenants (Flow 1).
* **Key Elements (React-Admin Concept):**
    1.  **View (List):** A React-Admin `<ListGuesser>` or `<Datagrid>` component displaying all tenants (ID, Name, Status).
    2.  **Actions:** A "Create" button (above the list), plus "Edit" and "Delete" buttons on each row.
    3.  **View (Create/Edit):** A `<SimpleForm>` component containing text inputs for Tenant Name and Tenant ID, and any other configuration fields.
* **Interaction Notes:** This view will utilize standard React-Admin data provider hooks to interact with the EAF's API (per Flow 2, which sets the CRUD pattern).
* **Design File Reference:** N/A. (This document is the specification).

-----

## Component Library / Design System

#### Design System Approach

Our approach is to **adopt an existing design system**, not create a new one from scratch:

1.  **Internal Operator Portal:** The EAF Operator Portal is built using **React-Admin**. React-Admin natively uses the **Material-UI (MUI)** component library. Therefore, all new custom components developed for the internal portal MUST be built using MUI components and adhere to MUI standards and patterns.
2.  **External Product UIs:** The EAF framework is "headless." External product teams (like ZEWSSP/DPCM) are free to use their own design systems and component libraries (e.g., standard React, Vaadin, or TUI) as defined in the brief. The EAF only supplies the secure API endpoints for those UIs to consume.

-----

#### Core Components

This list identifies new, domain-specific components the EAF portal will require, built using the adopted (MUI) library.

**Component:** TenantSelector (Global UI Component)

* **Purpose:** Allows a user (like a Security Admin) to view and select their active Tenant Context. This component visualizes the Tenant ID required by the security model (Epic 4).
* **Variants:**
    * **Read-only:** (For standard users) Displays the name of the tenant context they are currently in.
    * **Selectable:** (For Super-Admins) A dropdown allowing the user to switch their active Tenant Context (which will refresh their JWT/permissions).
* **States:** Loading (fetching tenant list), Loaded (displaying tenant), Error (cannot fetch tenants).

-----

## Branding & Style Guide

#### Visual Identity

* **Brand Guidelines:** (None provided). This specification will derive the visual style from the provided corporate webpage screenshot (Turn 97).

#### Color Palette

(Derived from the Axians website screenshot)

| Color Type | Hex Code | Usage |
| :--- | :--- | :--- |
| Primary | {{AXIANS\_DARK\_BLUE}} | Primary headers, key text, navigation background. (Derived from screenshot header/logo text). |
| Secondary | {{AXIANS\_MED\_GREY}} | Body copy, secondary text. (Derived from screenshot paragraph text). |
| Accent | {{AXIANS\_MAGENTA}} | All interactive elements: buttons, links, focus rings. (Derived from screenshot button/swirl graphic). |
| Success | (MUI Green default) | Positive feedback, confirmations. |
| Warning | (MUI Orange default) | Cautions, important notices. |
| Error | (MUI Red default) | Errors, destructive actions. |
| Neutral | (MUI Grey palette/White) | Text, borders, backgrounds. (Background is white per screenshot). |

#### Typography

* **Font Families:**
    * **Primary (Sans-Serif):** The screenshot uses a clean, modern, lightweight sans-serif font (e.g., Roboto, Inter, or a proprietary Axians font. We will default to the MUI/React-Admin default (Roboto) unless the specific font name is provided).
* **Type Scale:**
    * (Scale will adhere to standard Material Design type ramp).

#### Iconography

* **Icon Library:** We will use the **Material Icons** library.
* **Usage Guidelines:** This is the default and recommended icon set for the Material-UI component library, ensuring visual consistency with our chosen React-Admin design system.

#### Spacing & Layout

* **Grid System:** Will adhere to the standard **MUI responsive grid** (12-column layout).
* **Spacing Scale:** Will use the standard **MUI 8px spacing scale** (e.g., `theme.spacing(1) = 8px`) for all margins, padding, and layout gutters to ensure consistency.

-----

## Accessibility Requirements

#### Compliance Target

* **Standard:** **WCAG 2.1 Level A**. This is the baseline level of compliance, covering the most fundamental accessibility barriers (per user refinement, Turn 100).

#### Key Requirements

* **Visual:**
    * Color must not be used as the *only* visual means of conveying information or indicating an action.
* **Interaction:**
    * **Keyboard Navigation:** All functionality of the interface MUST be operable through a keyboard interface.
* **Content:**
    * **Alternative Text:** All non-text content (images, icons) that conveys meaning must have a text alternative (alt text).
    * **Form Labels:** All form inputs must have labels that are programmatically associated with them.

#### Testing Strategy

* Testing will focus on automated scans (e.g., Axe) and manual keyboard-only navigation testing to confirm these fundamental Level A requirements are met.

-----

## Responsiveness Strategy

This strategy is driven by our adoption of the **Material-UI (MUI)** component library and the PRD requirement that this is a "Primarily desktop-focused" application that "must be functional on tablets".

#### Breakpoints

We will adopt the **standard, default breakpoints of the Material-UI library**:

| Breakpoint | Min Width (Inclusive) | Max Width (Exclusive) | Target Devices |
| :--- | :--- | :--- | :--- |
| Mobile (xs/sm) | 0px | 900px | Mobile Phones (Portrait & Landscape) |
| Tablet (md) | 900px | 1200px | Tablets (e.g., iPad) |
| Desktop (lg) | 1200px | 1536px | Standard Desktops / Laptops |
| Wide (xl) | 1536px | - | Wide Screen Desktops |

#### Adaptation Patterns

* **Layout Strategy:** We will follow a **Desktop-First** approach, aligning with our user context. Designs will prioritize information density and usability at the `lg` and `xl` breakpoints.
* **Navigation Changes:** The primary navigation (React-Admin sidebar) will be persistent (open) on `lg`/`xl`. It may collapse to an icon-only "mini-variant" on `md` (Tablet). On `sm`/`xs` (Mobile), the sidebar MUST default to a hidden "temporary drawer" (hamburger menu).
* **Content Priority:** The UI must remain fully functional on `md` (Tablet). Mobile (`xs`/`sm`) views are a secondary concern; complex data grids and forms only need to be usable, not optimized, on these smallest screens.

-----

## Animation & Micro-interactions

#### Motion Principles

Our motion principle is **Subtlety and Function**, aligning with our "Utility First" goal. Animations should only be used to provide meaningful feedback, guide focus, or smooth a layout transition.

1.  **Adopt Defaults:** We will rely almost exclusively on the standard, built-in animations provided by the **Material-UI (MUI)** component library (e.g., button ripple, modal fade).
2.  **Performance First:** Animations must be lightweight and not impact perceived performance.
3.  **Accessibility Mandate:** All animations MUST respect the user's `prefers-reduced-motion` browser setting (per WCAG Level A goals).

#### Key Animations

* **1. MUI Component Feedback (Standard):** We will utilize the default MUI animations (Button ripple effect, Modal fade-in, Drawer slide-in).
* **2. Loading States:** Skeletons (MUI Skeleton component) or subtle Spinners (MUI CircularProgress) must be used for any data fetch that takes longer than 300ms.
* **3. Focus Indicators:** All interactive elements must use a clear and smooth transition for their keyboard focus state.

-----

## Performance Considerations

This section defines the performance goals required to fix the "slow performance" pain point of the legacy DCA.

#### Performance Goals

Our UX performance goals are based on Google's Core Web Vitals, ensuring the portal feels modern and responsive:

* **Page Load (LCP):** The initial load of the React-Admin portal (dashboard) must achieve a "Good" Largest Contentful Paint (LCP) score (\<2.5 seconds).
* **Interaction Response (INP):** All interactive elements (button clicks, form submissions, data fetching) must provide feedback in \<200ms.
* **Layout Stability (CLS):** The UI must have zero Cumulative Layout Shift (CLS). Loading data must not cause existing UI elements to jump or reflow.

#### Design Strategies

To meet these goals, the following strategies are mandated from a UX design perspective:

1.  **Lazy Loading Routes:** The React application must be code-split. The initial bundle must only contain the core application shell and the Dashboard. All other sections (Security, Licensing, Post-MVP modules) must be lazy-loaded.
2.  **Mandatory Pagination:** All data-dense grids (e.g., Tenant lists, License lists) MUST use server-side pagination and filtering. Loading an unbounded data set into the client is forbidden.
3.  **Skeleton States:** All data containers (grids, charts, detail panels) that fetch data asynchronously MUST use Skeleton loaders (per our Animation spec) that match the component's geometry to prevent CLS.
