# @axians/eaf-admin-shell

Enterprise Application Framework - React-Admin Shell Infrastructure

A reusable React-Admin shell providing authentication, theming, and shared components for EAF product modules. This package implements Story 4.5's framework/product separation principle for the frontend.

## Features

- **JWT Authentication** with Keycloak OIDC
- **Multi-Tenant Isolation** with automatic X-Tenant-ID header injection
- **RFC 7807 Error Mapping** for user-friendly error messages
- **Axians Branded Theme** (Material-UI based)
- **Shared UI Components** (EmptyState, LoadingSkeleton, BulkDeleteWithConfirm, TypeToConfirmDelete)
- **Plugin Architecture** for dynamic resource registration

## Installation

```bash
npm install @axians/eaf-admin-shell
```

## Usage

### Basic Setup

```typescript
import { AdminShell } from '@axians/eaf-admin-shell';
import { productResource, licenseResource } from '@eaf/product-licensing-server-ui';

const App = () => (
  <AdminShell
    resources={[productResource, licenseResource]}
    apiBaseUrl="http://localhost:8080/api/v1"
    keycloakConfig={{
      realm: 'eaf',
      clientId: 'eaf-admin',
      serverUrl: 'http://localhost:8180',
    }}
  />
);

export default App;
```

### Creating Product UI Modules

Product UI modules consume the admin shell infrastructure:

```typescript
// products/licensing-server/ui-module/src/resources/product/List.tsx
import { List, Datagrid, TextField } from 'react-admin';
import { EmptyState, LoadingSkeleton } from '@axians/eaf-admin-shell';

export const ProductList = () => (
  <List empty={<EmptyState message="No products yet" />}>
    <Datagrid>
      <TextField source="name" />
      <TextField source="sku" />
    </Datagrid>
  </List>
);
```

### Resource Registration

```typescript
// products/licensing-server/ui-module/src/index.ts
import { ProductList } from './resources/product/List';
import type { ResourceConfig } from '@axians/eaf-admin-shell';

export const productResource: ResourceConfig = {
  name: 'products',
  list: ProductList,
  create: ProductCreate,
  edit: ProductEdit,
  show: ProductShow,
};
```

## API Reference

### AdminShell

Main shell component with resource registration.

**Props:**
- `resources: ResourceConfig[]` - Array of resource configurations
- `apiBaseUrl?: string` - API base URL (default: 'http://localhost:8080/api/v1')
- `keycloakConfig?: KeycloakConfig` - Keycloak configuration
- `customTheme?: Theme` - Custom Material-UI theme override

### createDataProvider

Factory function for creating data provider with JWT auth and tenant injection.

```typescript
import { createDataProvider } from '@axians/eaf-admin-shell';

const dataProvider = createDataProvider('http://localhost:8080/api/v1');
```

### createAuthProvider

Factory function for creating Keycloak OIDC auth provider.

```typescript
import { createAuthProvider } from '@axians/eaf-admin-shell';

const authProvider = createAuthProvider({
  realm: 'eaf',
  clientId: 'eaf-admin',
  serverUrl: 'http://localhost:8180',
});
```

### Shared Components

#### EmptyState

Empty state component for lists with no data.

```typescript
<List empty={<EmptyState message="No items yet" ctaLabel="Create First Item" ctaLink="/items/create" />}>
  <Datagrid>...</Datagrid>
</List>
```

#### LoadingSkeleton

Loading skeleton for perceived performance.

```typescript
{loading && <LoadingSkeleton rows={5} />}
```

#### BulkDeleteWithConfirm

Bulk delete with type-to-confirm dialog.

```typescript
<Datagrid bulkActionButtons={<BulkDeleteWithConfirm />}>
  ...
</Datagrid>
```

#### TypeToConfirmDelete

Single-item delete with name confirmation.

```typescript
<TypeToConfirmDelete resource="products" confirmField="name" />
```

## Security Considerations

⚠️ **IMPORTANT**: This package stores JWT tokens in browser `localStorage` for MVP simplicity. This creates an XSS attack surface.

**MVP Mitigations**:
- Short-lived access tokens (15 minutes)
- Automatic token refresh
- DOMPurify sanitization for user input
- Backend CSP headers required

**Recommended for Production**:
- Migrate to httpOnly cookies (requires backend session support)
- Implement BFF (Backend-for-Frontend) pattern

## Architecture

This package follows the **hybrid micro-frontend pattern**:

```
framework/admin-shell/           # Infrastructure (THIS PACKAGE)
products/*/ui-module/            # Domain resources (consume this package)
apps/admin/                      # Composition layer
```

**Framework Responsibilities** (this package):
- ✅ Authentication (Keycloak OIDC)
- ✅ Data provider (JWT, tenant injection, error mapping)
- ✅ Theming (Axians branding)
- ✅ Shared components

**Product Responsibilities** (consuming packages):
- ✅ Domain-specific CRUD resources
- ✅ Business logic
- ✅ Product-specific UI components

## Development

```bash
# Install dependencies
npm install

# Run tests
npm test

# Type check
npm run typecheck

# Build library
npm run build

# Lint
npm run lint
npm run format
```

## License

Apache-2.0

## Related Documentation

- [Frontend Architecture Decision](../../docs/architecture/frontend-architecture-decision.md)
- [Story 7.4a](../../docs/stories/7.4a.create-react-admin-shell-framework.story.md)
- [UX Specification](../../docs/ux/story-7.4-frontend-spec-supplement.md)
