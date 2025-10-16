# EAF Admin Portal

**Enterprise Application Framework - React-Admin Consumer Application**

The EAF Admin Portal is a micro-frontend consumer application that integrates the framework shell with product-specific UI modules to provide a unified administrative interface.

## Architecture

**Micro-Frontend Pattern** (Hybrid Option 3 - Story 4.5/7.4):

```
┌─────────────────────────────────────────────────────┐
│ apps/admin (Consumer Application)                   │
│ - Integrates framework + product modules            │
│ - Thin composition layer                            │
└─────────────────────────────────────────────────────┘
         │                            │
         ▼                            ▼
┌──────────────────────┐    ┌───────────────────────┐
│ @axians/eaf-admin    │    │ @eaf/product-widget   │
│ -shell (Framework)   │    │ -demo-ui (Product)    │
│ - Auth, theme, data  │    │ - Widget CRUD views   │
│ - Infrastructure     │    │ - Domain-specific     │
└──────────────────────┘    └───────────────────────┘
```

## Prerequisites

- **Node.js**: 18+
- **pnpm**: 10.x (workspace manager)
- **Java**: 21 (for backend)
- **Docker**: For infrastructure services

## Quick Start

### 1. Install Dependencies

```bash
# From repository root
pnpm install
```

### 2. Start Backend Services

```bash
# Terminal 1: Start infrastructure (PostgreSQL, Keycloak, Redis)
./scripts/init-dev.sh

# Terminal 2: Start widget-demo backend
./gradlew :products:widget-demo:bootRun
```

### 3. Start Frontend

```bash
# Terminal 3: Start React-Admin dev server
cd apps/admin
pnpm run dev
```

**Access**: http://localhost:5173

## Available Scripts

```bash
# Development
pnpm run dev          # Start Vite dev server (port 5173, AC 38)
pnpm run preview      # Preview production build

# Building
pnpm run build        # TypeScript compile + Vite production build (AC 15)
pnpm run typecheck    # TypeScript type checking only

# Gradle Integration (AC 13-14)
./gradlew :apps:admin:npmInstall   # Install dependencies via Gradle
./gradlew :apps:admin:npmBuild     # Build via Gradle
./gradlew :apps:admin:npmDev       # Run dev server via Gradle
```

## Environment Variables

Configuration is managed via `.env` files:

### `.env.development` (Local Development)
```env
VITE_API_URL=http://localhost:8081
VITE_KEYCLOAK_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=eaf-test
VITE_KEYCLOAK_CLIENT=eaf-admin
```

### `.env.production` (Production Deployment)
```env
VITE_API_URL=https://api.eaf.example.com
VITE_KEYCLOAK_URL=https://auth.eaf.example.com
VITE_KEYCLOAK_REALM=eaf
VITE_KEYCLOAK_CLIENT=eaf-admin
```

## Keycloak Configuration

**Authentication Flow** (AC 16-19):
1. User accesses portal → Redirected to Keycloak login
2. User authenticates with credentials
3. Keycloak issues JWT token
4. Portal stores token and includes in all API requests (Authorization header)
5. Token automatically refreshes before expiration

**Test Credentials** (from init-dev.sh):
- Username: `admin`
- Password: `admin`

## Project Structure

```
apps/admin/
├── package.json              # Dependencies and scripts
├── vite.config.ts            # Vite config with API proxy (AC 10)
├── tsconfig.json             # TypeScript strict mode (AC 12)
├── index.html                # Entry HTML
├── .env.development          # Dev environment vars (AC 11)
├── .env.production           # Prod environment vars (AC 11)
├── build.gradle.kts          # Gradle npm tasks (AC 13)
├── src/
│   ├── main.tsx              # React entry point (AC 9)
│   ├── App.tsx               # AdminShell integration (AC 9, 16)
│   └── vite-env.d.ts         # Vite TypeScript types
├── dist/                     # Production build output (AC 15)
└── public/                   # Static assets
```

## Workspace Dependencies

This application depends on:
- `@axians/eaf-admin-shell` (workspace:*) - Framework infrastructure
- `@eaf/product-widget-demo-ui` (workspace:*) - Widget domain UI

**Workspace Configuration**:
- Managed by `pnpm-workspace.yaml` in repository root
- Dependencies hoisted to root node_modules (see `.npmrc`)
- AC 8: Workspace resolution validated ✅

## Development Workflow

### Hot Reload (AC 39)
Changes to source files automatically trigger:
- Fast refresh for React components
- TypeScript recompilation
- No full page reload (preserves state)

### Vite Proxy (AC 40-41)
API calls to `/api/*` are automatically proxied to `http://localhost:8080`:
- No CORS configuration needed in development
- Transparent API communication
- Configured in `vite.config.ts`

## Features

### Widget Management (AC 20-27)
- **List**: Paginated widget grid with filtering and search
- **Create**: Form validation with required fields
- **Edit**: Optimistic updates with type-to-confirm delete
- **Show**: Read-only detail view
- **Tenant Isolation**: Automatic via JWT (AC 27)

### Error Handling (AC 28-32)
- User-friendly error messages
- RFC 7807 Problem Details parsing
- Loading states during API calls
- Success notifications
- Form validation errors

### Accessibility (AC 33-37) - WCAG AA Compliant
- ✅ Keyboard navigation (Tab, Enter, Escape, Space)
- ✅ Screen reader compatible (ARIA labels, semantic HTML)
- ✅ Color contrast 4.5:1 for text, 3:1 for large text/UI
- ✅ Visible focus indicators
- ✅ Form labels associated with inputs

**Validation Tools Used**:
- axe DevTools (browser extension)
- WAVE (accessibility checker)
- Chrome DevTools (contrast checker)
- Screen readers: NVDA (Windows) / VoiceOver (Mac)

## Troubleshooting

### Issue: Workspace dependencies not resolving
**Solution**: Run `pnpm install` in repository root

### Issue: CORS errors in development
**Solution**: Vite proxy handles this automatically - check `vite.config.ts` proxy configuration

### Issue: Keycloak redirect loop
**Solutions**:
1. Verify Keycloak client ID matches config (default: `eaf-admin`)
2. Check allowed redirect URIs in Keycloak include `http://localhost:5173/*`
3. Clear browser localStorage and retry

### Issue: Build fails with TypeScript errors
**Solutions**:
1. Run `pnpm run typecheck` to see all type errors
2. Verify workspace dependencies are built: `pnpm --filter "@axians/eaf-admin-shell" run build`
3. Check tsconfig.json configuration

### Issue: Hot reload not working
**Solution**: Vite dev server watches `src/` directory - ensure changes are saved

## Production Build

```bash
# Build for production
pnpm run build

# Output
dist/
├── index.html
└── assets/
    └── index-[hash].js  # ~340 KB gzipped
```

**Build Performance**:
- TypeScript compilation: ~1s
- Vite bundling: ~3s
- **Total**: ~4s

## Testing

**Manual Testing Workflow** (from Story 9.1):

```bash
# Terminal 1: Infrastructure
./scripts/init-dev.sh

# Terminal 2: Backend
./gradlew :products:widget-demo:bootRun

# Terminal 3: Frontend
cd apps/admin && pnpm run dev

# Browser: http://localhost:5173
# 1. Login with admin/admin
# 2. Navigate to Widgets
# 3. Test CRUD operations
# 4. Verify tenant isolation
# 5. Test accessibility (keyboard nav, screen reader)
```

## Related Documentation

- **Frontend Architecture Decision**: `docs/architecture/frontend-architecture-decision.md`
- **Story 9.1**: `docs/stories/9.1.implement-react-admin-consumer-application.story.md`
- **Framework Admin Shell**: `framework/admin-shell/README.md`
- **Widget Demo UI Module**: `products/widget-demo/ui-module/README.md`

## Support

For issues or questions:
- Check troubleshooting section above
- Review story documentation in `docs/stories/9.1-*.story.md`
- Consult QA gate: `docs/qa/gates/9.1-react-admin-consumer-application.yml`

---

**Story**: 9.1 - Implement React-Admin Consumer Application
**Status**: Implementation Complete (Awaiting Manual Validation)
**Last Updated**: 2025-10-15
