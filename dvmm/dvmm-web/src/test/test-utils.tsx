import type { ReactElement, ReactNode } from 'react'
import { render, type RenderOptions } from '@testing-library/react'
import { AuthProvider } from 'react-oidc-context'
import { vi } from 'vitest'

// Mock auth context values
interface MockAuthContextValue {
  isAuthenticated?: boolean
  isLoading?: boolean
  error?: Error | null
  user?: {
    access_token?: string
    profile?: {
      name?: string
      preferred_username?: string
    }
  } | null
  signinRedirect?: () => Promise<void>
  signoutRedirect?: () => Promise<void>
}

const defaultMockAuth: MockAuthContextValue = {
  isAuthenticated: true,
  isLoading: false,
  error: null,
  user: {
    access_token: 'mock-token',
    profile: {
      name: 'Test User',
      preferred_username: 'testuser',
    },
  },
  signinRedirect: vi.fn(),
  signoutRedirect: vi.fn(),
}

// Mock AuthProvider that provides controlled auth state
export function MockAuthProvider({
  children,
  value = {},
}: {
  children: ReactNode
  value?: Partial<MockAuthContextValue>
}) {
  // Merge default with overrides (kept for future use when we need stateful mocks)
  void value

  // Create a mock OIDC config that satisfies the type requirements
  const mockOidcConfig = {
    authority: 'http://localhost:8180/realms/dvmm',
    client_id: 'dvmm-web',
    redirect_uri: 'http://localhost:5173/callback',
    scope: 'openid profile email',
    // Override the internal auth state
    onSigninCallback: vi.fn(),
  }

  return (
    <AuthProvider {...mockOidcConfig}>
      {/* We'll use a context override pattern in tests */}
      {children}
    </AuthProvider>
  )
}

// Create a custom hook mock for useAuth
export const createMockUseAuth = (overrides: Partial<MockAuthContextValue> = {}) => {
  const mockAuth = { ...defaultMockAuth, ...overrides }
  return () => mockAuth
}

// Custom render function with providers
interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  authState?: Partial<MockAuthContextValue>
}

export function customRender(
  ui: ReactElement,
  { authState, ...options }: CustomRenderOptions = {}
) {
  return render(ui, {
    wrapper: ({ children }) => (
      <MockAuthProvider value={authState}>{children}</MockAuthProvider>
    ),
    ...options,
  })
}

// Re-export everything from testing-library
export * from '@testing-library/react'
export { customRender as render }
