# The DVMM Design System

**Building a consistent, accessible, and beautiful UI.**

We don't build UI components from scratch (HTML/CSS). We assemble them using a modern, composition-based stack centered around **shadcn/ui** and **Tailwind CSS**.

---

## The Stack

1.  **Tailwind CSS:** Utility-first CSS. Instead of writing `.btn { padding: 10px; }`, we write `className="p-4"`.
    *   See [Tailwind CSS docs](https://tailwindcss.com/docs).
2.  **Radix UI:** Headless, accessible primitives. Radix handles the *logic* (keyboard navigation, focus management, ARIA attributes) but has *no styles*.
    *   See [Radix UI docs](https://www.radix-ui.com/docs/primitives/overview/introduction).
3.  **shadcn/ui:** Re-usable components built with Radix and Tailwind. It's not a library you install (`npm install shadcn`); it's code you copy-paste into your project. This gives us full ownership and customization.
    *   See [shadcn/ui docs](https://ui.shadcn.com/).
4.  **Lucide React:** Our icon set. Clean, consistent SVG icons.
    *   See [Lucide React docs](https://lucide.dev/).

## Visual Identity: "Tech Teal"

Our primary brand color is a professional, trustworthy Teal.

*   **Primary:** `teal-600` (#0d9488) - Actions, Buttons, Active States.
*   **Secondary:** `slate-900` (#0f172a) - Headings, Navigation.
*   **Background:** `slate-50` (#f8fafc) - App Background.
*   **Surface:** `white` (#ffffff) - Cards, Modals.

### Design Tokens

To maintain consistency, we use standard tokens:

*   **Typography:**
    *   **H1:** `text-3xl font-bold tracking-tight` (Page Titles)
    *   **H2:** `text-2xl font-semibold tracking-tight` (Section Headers)
    *   **Body:** `text-sm text-muted-foreground` (Descriptions)
*   **Spacing:** Based on the 4px grid. Standard padding is `p-6` (24px) for page containers and `p-4` (16px) for cards.
*   **Shadows:** `shadow-sm` for cards, `shadow-md` for dropdowns/modals.
*   **Animation:** `duration-200 ease-in-out` for hover states and transitions.

## Component Library (shadcn-admin-kit)

We use a starter kit called **shadcn-admin-kit** (an internal/external starter based on shadcn/ui) which gives us a head start on complex admin layouts (Sidebar, Header, Dashboard grid). It composes standard shadcn components into page-level structures.

### Key Components

*   **Card:** The building block of our dashboard. Used for stats, forms, and lists. Note: We use a custom wrapper around the base Card to support props like `title`, `value`, and `icon`.
*   **DataTable:** A powerful table with sorting, filtering, and pagination (powered by TanStack Table).
*   **Sheet:** A side-drawer modal. Perfect for "Edit" forms or filters without leaving the page.
*   **Command:** A generic "Command Palette" (Ctrl+K) for quick navigation.

## Design Principles

### 1. Consistency
A "Delete" button should look the same on the Dashboard as it does in the Settings. Use the predefined variants (`variant="destructive"`).

### 2. Accessibility (a11y)
We build for everyone.
*   **Keyboard:** Every action must be doable without a mouse.
*   **Contrast:** Text must meet WCAG AA contrast ratios.
*   **Screen Readers:** Images must have `alt` text. Inputs must have labels. Radix UI handles most of this for us.
*   **Standards:** We adhere to [WCAG 2.1 Level AA](https://www.w3.org/TR/WCAG21/).

### 3. Mobile Responsiveness
We design "Mobile First".
*   On phone: The sidebar collapses into a hamburger menu. Tables switch to card views or scroll horizontally.
*   On desktop: We use the available space for dense data displays.

## How to Build a New Screen

1.  **Layout:** Start with the `PageContainer`.
2.  **Structure:** Use `Grid` or `Flex` (Tailwind `grid` / `flex`) to arrange content.
3.  **Components:** Import primitives from `@/components/ui`.
4.  **Polish:** Add spacing (`gap-4`, `p-6`) and typography (`text-xl font-bold`).

```tsx
// Example Page
export default function Dashboard() {
  return (
    <div className="p-6 space-y-6">
      <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
      
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card title="Total VMs" value="12" icon={<Server />} />
        <Card title="Pending" value="3" icon={<Clock />} />
      </div>
    </div>
  )
}
```
