# Frontend Implementation Guidance

## Directory Blueprint

```plaintext
apps/admin/src/
├── app/                    # App shell, layout, theme, routing bootstrap
├── components/             # Shared presentational components (`*.component.tsx`)
├── features/
│   ├── dashboard/
│   ├── security/
│   │   └── tenants/
│   └── licensing/
│       ├── products/
│       └── licenses/
├── pages/                  # Route-entry components (`*.view.tsx`)
├── providers/              # DataProvider, AuthProvider, WebSocket hooks
├── services/               # API interaction utilities (`*.service.ts`)
├── state/                  # Zustand stores and contexts
├── testing/                # RTL helpers, fixtures
└── index.tsx
```

Naming conventions:

  * Views end with `.view.tsx`; reusable components end with `.component.tsx`.
  * Feature hooks live in `features/<domain>/hooks/` and must be prefixed with `use`.
  * Storybook stories co-locate next to components as `<Name>.stories.tsx`.

## Component Template

All new components follow this accessibility-first template:

```tsx
type TenantSelectorProps = {
  value: string;
  onChange: (tenantId: string) => void;
  disabled?: boolean;
};

export function TenantSelector({ value, onChange, disabled = false }: TenantSelectorProps) {
  return (
    <Autocomplete
      aria-label="tenant selector"
      options={useTenantOptions()}
      value={value}
      onChange={(_, id) => onChange(String(id))}
      loadingText="Loading tenants"
      disabled={disabled}
      data-testid="tenant-selector"
    />
  );
}
```

Components must document expected states in JSDoc, expose `data-testid` selectors, and include keyboard-friendly props.

## Routing & Resource Table

| Route | Resource Key | Chunk | Roles | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `/dashboard` | `dashboard` | `dashboard.chunk.tsx` | Operator, Security | Default landing page with system health cards |
| `/security/tenants` | `tenants` | `security-tenants.chunk.tsx` | Security Admin | Tenant CRUD, RLS policy indicators |
| `/products` | `products` | `licensing-products.chunk.tsx` | Operator | SKU uniqueness guard, bulk import hooks |
| `/licenses` | `licenses` | `licensing-licenses.chunk.tsx` | Operator | Wizard flow honoring eventual consistency |

Register routes in `app/app-routes.tsx` and document breadcrumbs, access roles, and projection dependencies.

## Data Provider & WebSocket Pattern

  * `providers/data-provider.ts` wraps React-Admin `fetchJson` to inject Keycloak tokens and convert RFC 7807 payloads into RA-friendly errors.
  * `providers/projection-socket.ts` manages the WebSocket connection, subscribing to projection channels (`tenants`, `products`, `licenses`) and dispatching `invalidateResource` events into Zustand stores.
  * New projections extend the `ProjectionChannel` union type and register handlers in `projection-handlers.ts`.

## Developer Experience Assets

  * Storybook stories are required for any exported component.
  * RTL tests live in `__tests__/` folders and use the shared `renderWithProviders` helper plus `axe` accessibility assertions.
  * CI enforces `npm run lint`, `npm test`, and Chromatic visual regression checks on every PR.

-----
