import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useIsAdmin } from './useIsAdmin'

// Mock react-oidc-context with vi.hoisted for proper ESM mocking
const mockUseAuth = vi.hoisted(() =>
  vi.fn(() => ({
    user: {
      access_token: 'test-token',
      profile: {
        realm_access: {
          roles: ['admin', 'user'],
        },
      },
    },
    isAuthenticated: true,
  }))
)

vi.mock('react-oidc-context', () => ({
  useAuth: mockUseAuth,
}))

describe('useIsAdmin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Reset to admin user by default
    mockUseAuth.mockReturnValue({
      user: {
        access_token: 'test-token',
        profile: {
          realm_access: {
            roles: ['admin', 'user'],
          },
        },
      },
      isAuthenticated: true,
    })
  })

  describe('admin role detection', () => {
    it('returns true when user has admin role', () => {
      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(true)
    })

    it('returns false when user lacks admin role', () => {
      mockUseAuth.mockReturnValue({
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['user'],
            },
          },
        },
        isAuthenticated: true,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(false)
    })

    it('returns false when realm_access.roles is empty', () => {
      mockUseAuth.mockReturnValue({
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: [],
            },
          },
        },
        isAuthenticated: true,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(false)
    })

    it('returns false when realm_access is undefined', () => {
      mockUseAuth.mockReturnValue({
        user: {
          access_token: 'test-token',
          profile: {},
        },
        isAuthenticated: true,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(false)
    })

    it('returns false when profile is undefined', () => {
      mockUseAuth.mockReturnValue({
        user: {
          access_token: 'test-token',
          profile: undefined,
        },
        isAuthenticated: true,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(false)
    })
  })

  describe('unauthenticated state', () => {
    it('returns false when user is null', () => {
      mockUseAuth.mockReturnValue({
        user: null,
        isAuthenticated: false,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(false)
    })

    it('returns false when user is undefined', () => {
      mockUseAuth.mockReturnValue({
        user: undefined,
        isAuthenticated: false,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(false)
    })
  })

  describe('role case sensitivity', () => {
    it('matches admin role case-sensitively (lowercase)', () => {
      mockUseAuth.mockReturnValue({
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['admin'],
            },
          },
        },
        isAuthenticated: true,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(true)
    })

    it('does not match ADMIN (uppercase)', () => {
      mockUseAuth.mockReturnValue({
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['ADMIN'],
            },
          },
        },
        isAuthenticated: true,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(false)
    })

    it('does not match Admin (mixed case)', () => {
      mockUseAuth.mockReturnValue({
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['Admin'],
            },
          },
        },
        isAuthenticated: true,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(false)
    })
  })

  describe('multiple roles', () => {
    it('returns true when admin is among multiple roles', () => {
      mockUseAuth.mockReturnValue({
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['user', 'admin', 'viewer'],
            },
          },
        },
        isAuthenticated: true,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(true)
    })

    it('returns false when admin is not among multiple roles', () => {
      mockUseAuth.mockReturnValue({
        user: {
          access_token: 'test-token',
          profile: {
            realm_access: {
              roles: ['user', 'viewer', 'editor'],
            },
          },
        },
        isAuthenticated: true,
      })

      const { result } = renderHook(() => useIsAdmin())
      expect(result.current).toBe(false)
    })
  })
})
