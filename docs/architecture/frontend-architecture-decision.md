# Frontend Architecture Decision: Hybrid Shell + Product UI Modules

**Decision Date**: 2025-10-03
**Decision Maker**: Product Owner Sarah
**Context**: Architectural course correction during Story 7.4 preparation
**Status**: **APPROVED - Option 3 (Hybrid Architecture)**

---

## Executive Summary

The EAF frontend architecture follows a **hybrid micro-frontend pattern** that mirrors the backend framework/product separation established in Story 4.5. This decision ensures framework publishability, clean architectural boundaries, and flexible product composition.

**Key Principle**: Apply Story 4.5's "Maven Publication Litmus Test" to frontend.

---

## Decision Context

### Problem Statement

During Story 7.4 preparation, we discovered the original assumption (single monolithic `apps/admin/` portal containing all product UIs) **violates the framework/product separation principle** established in Story 4.5.

**Story 4.5 Lesson**:
- ❌ **Before**: Widget domain lived in `framework/widget/` (violates Clean Architecture)
- ✅ **After**: Widget domain migrated to `products/widget-demo/` (infrastructure vs. domain separation)

**Question**: Should frontend follow the same pattern?
**Answer**: **YES** - Domain UIs belong in product modules, framework provides reusable shell infrastructure.

---

## Options Considered

### Option 1: Monolithic Admin Portal ❌ REJECTED

```
apps/admin/
└── src/resources/
    ├── widget/      # From widget-demo
    ├── product/     # From licensing-server
    └── license/     # From licensing-server
```

**Rejection Reasons**:
- Violates framework/product separation
- Tight coupling between products
- Not independently deployable
- Framework publishability problem (Widget UI ships with framework)

---

### Option 2: Per-Product Frontend ❌ REJECTED

```
products/widget-demo/ui/        # Standalone React app
products/licensing-server/ui/   # Standalone React app
```

**Rejection Reasons**:
- No code reuse (duplicate auth, theme, dataProvider)
- No unified operator experience
- Multiple logins required
- Higher maintenance burden

---

### Option 3: Hybrid Shell + Product UI Modules ✅ **APPROVED**

```
framework/admin-shell/           # Reusable infrastructure (Story 7.4a)
products/*/ui-module/            # Product-specific resources (Story 7.4b)
apps/admin/                      # Composition layer
```

**Approval Reasons**:
- ✅ Framework/product separation maintained
- ✅ Code reuse (shared auth, theme)
- ✅ Unified operator experience
- ✅ Independent product evolution
- ✅ Flexible composition (deploy subset of products)
- ✅ Framework publishability (`@axians/eaf-admin-shell` publishable to npm)

---

## Approved Architecture

### Final Structure

```
eaf-monorepo/
├── framework/
│   └── admin-shell/                    # Story 7.4a (NEW)
│       ├── package.json                # @axians/eaf-admin-shell@0.1.0
│       └── src/
│           ├── AdminShell.tsx          # Resource registration API
│           ├── providers/
│           │   ├── dataProvider.ts     # JWT, tenant, RFC 7807
│           │   └── authProvider.ts     # Keycloak OIDC
│           ├── theme/theme.ts          # Axians branding
│           └── components/
│               ├── EmptyState.tsx
│               ├── LoadingSkeleton.tsx
│               ├── BulkDeleteWithConfirm.tsx
│               └── TypeToConfirmDelete.tsx
├── products/
│   ├── widget-demo/
│   │   ├── src/main/kotlin/...         # Backend (Story 4.5)
│   │   └── ui-module/                  # Frontend (Story 7.4b)
│   │       ├── package.json            # @eaf/product-widget-demo-ui
│   │       └── src/
│   │           ├── resources/widget/
│   │           │   ├── List.tsx
│   │           │   ├── Create.tsx
│   │           │   ├── Edit.tsx
│   │           │   ├── Show.tsx
│   │           │   ├── types.ts
│   │           │   └── ResourceExport.ts  # export widgetResource
│   │           └── index.ts            # export { widgetResource }
│   └── licensing-server/
│       ├── src/main/kotlin/...         # Backend (Epic 8)
│       └── ui-module/                  # Frontend (Story 7.4b)
│           ├── package.json            # @eaf/product-licensing-server-ui
│           └── src/
│               ├── resources/product/
│               ├── resources/license/
│               └── index.ts            # export { productResource, licenseResource }
├── apps/
│   └── admin/                          # Composition app
│       ├── package.json                # Depends on framework + products
│       └── src/
│           └── main.tsx                # Composes all resources
└── shared/
    └── shared-types/                   # TypeScript shared types
```

---

## Integration Pattern

### Product UI Module Exports Resource

```typescript
// products/licensing-server/ui-module/src/resources/product/ResourceExport.ts
import { ProductList } from './List';
import { ProductCreate } from './Create';
import { ProductEdit } from './Edit';
import { ProductShow } from './Show';
import type { ResourceConfig } from '@axians/eaf-admin-shell';

export const productResource: ResourceConfig = {
  name: 'products',
  list: ProductList,
  create: ProductCreate,
  edit: ProductEdit,
  show: ProductShow,
};
```

```typescript
// products/licensing-server/ui-module/src/index.ts
export { productResource } from './resources/product/ResourceExport';
export { licenseResource } from './resources/license/ResourceExport';
```

### Composed App Registers Resources

```typescript
// apps/admin/src/main.tsx
import { AdminShell } from '@axians/eaf-admin-shell';
import { widgetResource } from '@eaf/product-widget-demo-ui';
import { productResource, licenseResource } from '@eaf/product-licensing-server-ui';

const App = () => (
  <AdminShell
    resources={[
      widgetResource,      // From widget-demo product
      productResource,     // From licensing-server product
      licenseResource,     // From licensing-server product
    ]}
    apiBaseUrl="http://localhost:8080/api/v1"
    keycloakConfig={{
      realm: 'eaf',
      clientId: 'eaf-admin',
      serverUrl: 'http://localhost:8180'
    }}
  />
);
```

---

## Dependency Boundaries

### Framework Admin Shell (MUST NOT)
- ❌ Import any product-specific types (Product, License, Widget)
- ❌ Contain any domain business logic
- ❌ Reference `products/*` directories
- ❌ Include domain-specific validation rules

### Framework Admin Shell (MUST)
- ✅ Provide reusable infrastructure (auth, theme, dataProvider)
- ✅ Export typed interfaces for product consumption (`ResourceConfig`)
- ✅ Handle cross-cutting concerns (error mapping, tenant injection, logging)
- ✅ Be publishable to npm registry without any product code

### Product UI Modules (MUST NOT)
- ❌ Duplicate authentication logic (use admin-shell's authProvider)
- ❌ Reimplement dataProvider (use admin-shell's createDataProvider)
- ❌ Create custom themes (use admin-shell's eafTheme)
- ❌ Reference other product modules

### Product UI Modules (MUST)
- ✅ Import and use framework/admin-shell infrastructure
- ✅ Export resource registration objects
- ✅ Contain product-specific CRUD components
- ✅ Reference backend API endpoints from own product

---

## Story Split Rationale

### Original Story 7.4 (Single Story) - REJECTED

**Scope**: Generate React-Admin resources into `apps/admin/`
**Problem**: Creates monolithic admin portal with mixed product concerns
**Violates**: Story 4.5's framework/product separation principle

### Split Approach - APPROVED

**Story 7.4a**: Create Framework Infrastructure
- **Scope**: Build `framework/admin-shell/` with reusable components
- **Output**: Publishable npm package `@axians/eaf-admin-shell`
- **Benefit**: Framework teams can evolve infrastructure independently

**Story 7.4b**: Create Product Generator
- **Scope**: CLI generates resources into `products/*/ui-module/`
- **Output**: Product-specific UI modules that import framework shell
- **Benefit**: Products own their UI, aligned with backend ownership

---

## Alignment with Story 4.5 Principles

| Backend (Story 4.5) | Frontend (Story 7.4a/b) | Principle |
|---------------------|----------------------|-----------|
| framework/cqrs (infrastructure) | framework/admin-shell (infrastructure) | Publishable libraries |
| products/widget-demo/domain (domain logic) | products/widget-demo/ui-module (domain UI) | Business-specific code |
| shared/shared-api (contracts) | shared/shared-types (contracts) | Cross-product types |
| Maven publication test | npm publication test | Publishability validation |

**Maven Publication Litmus Test** → **npm Publication Litmus Test**:
- **Question**: If we publish framework to registry, should Widget UI be included?
- **Answer**: **NO** ✅ - Widget is product domain, not framework infrastructure

---

## Benefits

### 1. Framework Publishability
- `@axians/eaf-admin-shell` can be published to npm registry
- Other companies can use EAF admin shell for their own products
- Framework contains zero customer-specific domain logic

### 2. Independent Product Evolution
- widget-demo UI changes don't affect licensing-server UI
- Products can version their ui-modules independently
- Breaking changes isolated to specific products

### 3. Flexible Deployment
```typescript
// Scenario 1: Full admin portal (all products)
<AdminShell resources={[widgetResource, productResource, licenseResource]} />

// Scenario 2: Licensing-only portal (customer-specific deployment)
<AdminShell resources={[productResource, licenseResource]} />

// Scenario 3: Single-tenant widget demo
<AdminShell resources={[widgetResource]} />
```

### 4. Code Reuse
- Authentication logic in framework (not duplicated)
- Theming in framework (consistent branding)
- Shared components (EmptyState, LoadingSkeleton) reused across products

### 5. Testability
- Framework shell tested independently
- Product UI modules tested independently
- Composition tested in apps/admin integration tests

---

## Migration Path

### Immediate (Sprint 1)
1. ✅ Update Epic 7 with Story 7.4a and 7.4b
2. ⏳ **Story 7.4a**: Implement framework/admin-shell (3-4 days)
3. ⏳ **Story 7.4b**: Implement product UI module generator (2-3 days)

### Short-Term (Sprint 2 - Epic 8)
4. ⏳ **Story 8.2**: Generate Product UI using `eaf scaffold ui-resource Product --module licensing-server`
5. ⏳ **Story 8.3**: Generate License UI using `eaf scaffold ui-resource License --module licensing-server`
6. ⏳ **Story 8.4**: Compose apps/admin with licensing-server ui-module resources

### Long-Term (Post-MVP)
7. 📅 Publish `@axians/eaf-admin-shell` to npm registry (internal or public)
8. 📅 Create product-specific admin portals (customer-facing, subset of resources)
9. 📅 Implement micro-frontend dynamic loading (load products on-demand)

---

## Impact on Epic 8

**Story 8.2/8.3 Commands Updated**:
```bash
# Old (monolithic assumption):
eaf scaffold ra-resource Product

# New (hybrid architecture):
eaf scaffold ui-resource Product --module licensing-server
```

**Story 8.4 Validation Updated**:
- Test framework/admin-shell resource registration
- Test product ui-module exports
- Test apps/admin composition

---

## Related Decisions

### Backend (Story 4.5)
- **Decision**: Move Widget from `framework/widget/` → `products/widget-demo/`
- **Principle**: Framework = infrastructure, Products = domain
- **Validation**: Maven publication litmus test

### Frontend (Story 7.4a/b)
- **Decision**: Create `framework/admin-shell/` + `products/*/ui-module/`
- **Principle**: Framework = infrastructure, Products = domain (same principle)
- **Validation**: npm publication litmus test

**Consistency**: Backend and frontend follow identical architectural patterns

---

## References

- **Story 4.5**: [Migrate Widget Domain to Product Module](../stories/4.5.migrate-widget-domain-to-product-module.story.md)
- **Story 7.4a**: [Create React-Admin Shell Framework](../stories/7.4a.create-react-admin-shell-framework.story.md)
- **Story 7.4b**: [Create Product UI Module Generator](../stories/7.4b.create-product-ui-module-generator.story.md)
- **Epic 7**: [Scaffolding CLI v1](../prd/epic-7-scaffolding-cli-v1.md)
- **UX Spec**: [Story 7.4 Frontend Spec Supplement](../ux/story-7.4-frontend-spec-supplement.md)

---

## Decision Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2025-10-03 | Option 3 (Hybrid) approved | Aligns with Story 4.5 framework/product separation, enables framework publishability, maintains code reuse |
| 2025-10-03 | Story 7.4 split into 7.4a + 7.4b | Framework shell (7.4a) provides infrastructure, generator (7.4b) creates product UI modules |
| 2025-10-03 | Epic 7 updated | Added Story 7.4a as prerequisite to 7.4b |

---

**Product Owner**: Sarah 📝
**Date**: 2025-10-03
**Status**: APPROVED - Ready for implementation
