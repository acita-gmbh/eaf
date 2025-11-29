import { createContext, useContext, type ReactElement, type ReactNode } from 'react'
import { render, type RenderOptions } from '@testing-library/react'
import { vi } from 'vitest'

// Mock auth context values matching react-oidc-context's AuthContextProps
interface MockAuthContextValue {
  isAuthenticated: boolean
  isLoading: boolean
  error: Error | null
  user: {
    access_token: string
    profile?: {
      name?: string
      preferred_username?: string
    }
  } | null
  signinRedirect: () => Promise<void>
  signoutRedirect: () => Promise<void>
}

const defaultMockAuth: MockAuthContextValue = {
  isAuthenticated: true,
  isLoading: false,
  error: null,
  user: {
    access_token: 'mock-access-token',
    profile: {
      name: 'Test User',
      preferred_username: 'testuser',
    },
  },
  signinRedirect: vi.fn(),
  signoutRedirect: vi.fn(),
}

// Create a mock context to replace react-oidc-context's AuthContext
const MockAuthContext = createContext<MockAuthContextValue>(defaultMockAuth)

/**
 * Mock useAuth hook for tests.
 * Use this when you need a component to access auth state in tests.
 */
export function useMockAuth(): MockAuthContextValue {
  return useContext(MockAuthContext)
}

/**
 * Mock AuthProvider that provides controlled auth state for testing.
 * Unlike the real AuthProvider, this doesn't require OIDC configuration
 * and provides the mock values directly to child components.
 */
export function MockAuthProvider({
  children,
  value = {},
}: {
  children: ReactNode
  value?: Partial<MockAuthContextValue>
}) {
  // Merge default values with any overrides
  const authValue: MockAuthContextValue = {
    ...defaultMockAuth,
    ...value,
    // Ensure nested user object is properly merged
    user: value.user === null ? null : {
      ...defaultMockAuth.user,
      ...value.user,
    },
  }

  return (
    <MockAuthContext.Provider value={authValue}>
      {children}
    </MockAuthContext.Provider>
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
