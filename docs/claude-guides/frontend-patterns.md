# Frontend Patterns Guide

> Referenced from CLAUDE.md - Read when working on React/TypeScript frontend code.

**Location:** `dvmm/dvmm-web/`
**Stack:** React 19 + TypeScript 5.9 + Vite 7.2 + Tailwind CSS 4 + shadcn/ui

## Commands

```bash
cd dvmm/dvmm-web
npm run dev          # Dev server (port 5173)
npm run build        # Type-check and build
npm run test         # Vitest unit tests
npm run test:e2e     # Playwright E2E tests
npm run lint         # ESLint
```

## Test File Convention

**Tests MUST be colocated with source files.** `__tests__` directories are forbidden by project convention.

```text
src/components/Button.tsx       # ✅
src/components/Button.test.tsx  # ✅
src/components/__tests__/...    # ❌ FORBIDDEN
```

## React Compiler Rules (Zero-Tolerance)

**Manual memoization is PROHIBITED.** React Compiler handles it automatically.

```tsx
// ❌ FORBIDDEN - ESLint will error
import { useMemo, useCallback, memo } from 'react'
const memoizedValue = useMemo(() => compute(a, b), [a, b])
const memoizedFn = useCallback(() => doSomething(a), [a])
const MemoizedComponent = memo(MyComponent)

// ✅ CORRECT
const value = compute(a, b)
const handleClick = () => doSomething(a)
function MyComponent() { ... }
```

### React Hook Form: useWatch over watch

```tsx
// ❌ watch() causes React Compiler lint warnings
const { watch } = useForm()
const value = watch('fieldName')

// ✅ useWatch is React Compiler compatible
const { control } = useForm()
const value = useWatch({ control, name: 'fieldName' })
```

### Read-only Props (Required)

```tsx
// ✅ REQUIRED - Wrap props with Readonly<>
export function Button({ label, onClick }: Readonly<ButtonProps>) { ... }

// ❌ FORBIDDEN - Mutable props
export function Button({ label, onClick }: ButtonProps) { ... }
```

### Floating Promises

**Use `void` for intentional fire-and-forget operations:**

```tsx
void navigate('/dashboard')  // React Router v6 returns Promise
void queryClient.invalidateQueries({ queryKey: ['my-requests'] })
void auth.signinRedirect({ state: { returnTo: location.pathname } })
```

## Vitest Patterns

### vi.hoisted() for Module Mocks

```tsx
const mockUseAuth = vi.hoisted(() =>
  vi.fn(() => ({ user: { access_token: 'test-token' }, isAuthenticated: true }))
)

vi.mock('react-oidc-context', () => ({ useAuth: mockUseAuth }))

it('handles unauthenticated state', () => {
  mockUseAuth.mockReturnValue({ user: null, isAuthenticated: false })
  // ... test
})
```

### Sequential Responses

```tsx
mockGetData
  .mockResolvedValueOnce({ status: 'PENDING' })   // First call
  .mockResolvedValueOnce({ status: 'APPROVED' })  // After refetch
```

## TanStack Query Polling

For real-time data (admin queues, dashboards):

```tsx
useQuery({
  queryKey: ['admin', 'pending-requests'],
  queryFn: fetchPendingRequests,
  staleTime: 10000,
  refetchInterval: 30000 + Math.floor(Math.random() * 5000),  // Jitter prevents thundering herd
  refetchIntervalInBackground: false,
  refetchOnWindowFocus: true,
})
```

## Playwright E2E Testing

Uses `@seontechnologies/playwright-utils`:

```tsx
import { test } from '@seontechnologies/playwright-utils/fixtures'
import { recurse } from '@seontechnologies/playwright-utils/recurse'

test('creates VM request', async ({ apiRequest }) => {
  const { status } = await apiRequest({
    method: 'POST',
    path: '/api/vm-requests',
    data: { vmName: 'web-01', cpuCores: 4 }
  })
  expect(status).toBe(201)
})

// Polling for async conditions
const result = await recurse(
  () => page.locator('[data-testid="status"]').textContent(),
  (text) => text === 'Provisioned',
  { timeout: 30000 }
)
```

### Security: No Dynamic RegExp

```tsx
// ❌ FORBIDDEN - ReDoS risk (CWE-1333)
await expect(page).toHaveURL(new RegExp(`/admin/requests/${requestId}`))

// ✅ CORRECT
await expect(page).toHaveURL(`/admin/requests/${requestId}`)
```
