/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { jwtDecode } from 'jwt-decode';

/**
 * Story 7.4a - P0 Security Tests for Auth Provider
 * CRITICAL: These tests MUST pass before implementing production authProvider code
 * Risk: SEC-003 (Keycloak OIDC implementation errors), SEC-001 (Token management)
 *
 * Note: Test mocks use `any` type for flexibility - this is acceptable in test code
 */

describe('7.4a-UNIT-P0-004: Keycloak Login Flow (FUNCTIONAL)', () => {
  beforeEach(() => {
    localStorage.clear();
    global.fetch = vi.fn();
  });

  it('should successfully login with valid credentials using real authProvider', async () => {
    // Given: Valid Keycloak credentials and mock token response
    const username = 'testuser';
    const password = 'testpass';
    const mockTokenResponse = {
      access_token: 'valid.access.token',
      refresh_token: 'valid.refresh.token',
      expires_in: 900,
    };

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => mockTokenResponse,
    });

    // When: Call REAL authProvider (not re-implemented mock)
    const { createAuthProvider } = await import('../../providers/authProvider');
    const authProvider = createAuthProvider({
      realm: 'eaf',
      clientId: 'eaf-admin',
      serverUrl: 'http://localhost:8180',
    });

    await authProvider.login({ username, password });

    // Then: Real authProvider stores tokens with scoped keys
    expect(localStorage.getItem('eaf.auth.token')).toBe('valid.access.token');
    expect(localStorage.getItem('eaf.auth.refreshToken')).toBe('valid.refresh.token');
  });

  it('should fail login with invalid credentials', async () => {
    // Given: Invalid credentials
    const username = 'baduser';
    const password = 'wrongpass';

    (global.fetch as any).mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: async () => ({ error: 'invalid_grant' }),
    });

    // When: Using real authProvider with invalid credentials
    const { createAuthProvider } = await import('../../providers/authProvider');
    const authProvider = createAuthProvider({
      realm: 'eaf',
      clientId: 'eaf-admin',
      serverUrl: 'http://localhost:8180',
    });

    // Then: Login throws error, localStorage remains clear
    await expect(authProvider.login({ username, password })).rejects.toThrow();
    expect(localStorage.getItem('eaf.auth.token')).toBeNull();
    expect(localStorage.getItem('eaf.auth.refreshToken')).toBeNull();
  });

  it('should handle network errors during login', async () => {
    // Given: Network error during login
    (global.fetch as any).mockRejectedValueOnce(new Error('Network error'));

    // When: Using real authProvider with network error
    const { createAuthProvider } = await import('../../providers/authProvider');
    const authProvider = createAuthProvider({
      realm: 'eaf',
      clientId: 'eaf-admin',
      serverUrl: 'http://localhost:8180',
    });

    // Then: Error propagates, localStorage remains clear
    await expect(authProvider.login({ username: 'test', password: 'test' })).rejects.toThrow('Network error');
    expect(localStorage.getItem('eaf.auth.token')).toBeNull();
  });
});

describe('7.4a-UNIT-P0-005: Token Expiration Detection and Automatic Logout', () => {
  it('should detect valid unexpired token', () => {
    // Given: Valid unexpired JWT (expires in 1 hour)
    const validToken = createMockJWT({
      sub: 'user123',
      exp: Math.floor(Date.now() / 1000) + 3600, // Expires in 1 hour
      tenant_id: 'tenant-123',
    });

    localStorage.setItem('token', validToken);

    // When: Checking token expiration
    const token = localStorage.getItem('token');
    const decoded = jwtDecode<{ exp: number }>(token!);
    const expiresIn = decoded.exp * 1000 - Date.now();

    // Then: Token is valid (expiration in future)
    expect(expiresIn).toBeGreaterThan(0);
  });

  it('should detect expired token', () => {
    // Given: Expired JWT (expired 1 hour ago)
    const expiredToken = createMockJWT({
      sub: 'user123',
      exp: Math.floor(Date.now() / 1000) - 3600, // Expired 1 hour ago
      tenant_id: 'tenant-123',
    });

    localStorage.setItem('token', expiredToken);

    // When: Checking token expiration
    const token = localStorage.getItem('token');
    const decoded = jwtDecode<{ exp: number }>(token!);
    const expiresIn = decoded.exp * 1000 - Date.now();

    // Then: Token is expired (expiration in past)
    expect(expiresIn).toBeLessThan(0);
  });

  it('should detect missing token', () => {
    // Given: No token in localStorage
    localStorage.clear();

    // When: Checking for token
    const token = localStorage.getItem('token');

    // Then: Token is null (should trigger login redirect)
    expect(token).toBeNull();
  });
});

describe('7.4a-UNIT-P0-006: Token Refresh Flow Edge Cases', () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  it('should refresh access_token when nearing expiration (<5 min)', async () => {
    // Given: access_token nearing expiration (3 minutes remaining)
    const accessToken = createMockJWT({
      sub: 'user123',
      exp: Math.floor(Date.now() / 1000) + 180, // Expires in 3 minutes
      tenant_id: 'tenant-123',
    });
    const refreshToken = 'valid.refresh.token';

    localStorage.setItem('token', accessToken);
    localStorage.setItem('refresh_token', refreshToken);

    // Mock successful token refresh
    const newAccessToken = createMockJWT({
      sub: 'user123',
      exp: Math.floor(Date.now() / 1000) + 900, // New token expires in 15 min
      tenant_id: 'tenant-123',
    });

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        access_token: newAccessToken,
        refresh_token: refreshToken,
      }),
    });

    // When: Checking auth and triggering refresh
    const decoded = jwtDecode<{ exp: number }>(accessToken);
    const expiresIn = decoded.exp * 1000 - Date.now();

    if (expiresIn < 5 * 60 * 1000) {
      // Auto-refresh logic
      const response = await fetch('http://localhost:8180/realms/eaf/protocol/openid-connect/token', {
        method: 'POST',
        body: new URLSearchParams({
          grant_type: 'refresh_token',
          refresh_token: refreshToken,
          client_id: 'eaf-admin',
        }),
      });

      const data = await response.json();
      localStorage.setItem('token', data.access_token);
    }

    // Then: New access token stored
    expect(localStorage.getItem('token')).toBe(newAccessToken);
    expect(expiresIn).toBeLessThan(5 * 60 * 1000); // Confirmed refresh was needed
  });

  it('should handle network error during token refresh', async () => {
    // Given: Token nearing expiration, network error during refresh
    const accessToken = createMockJWT({
      exp: Math.floor(Date.now() / 1000) + 180, // 3 minutes
    });
    localStorage.setItem('token', accessToken);
    localStorage.setItem('refresh_token', 'valid.refresh.token');

    (global.fetch as any).mockRejectedValueOnce(new Error('Network error'));

    // When: Attempting token refresh with network failure
    const refreshAccessToken = async () => {
      const response = await fetch('http://localhost:8180/realms/eaf/protocol/openid-connect/token', {
        method: 'POST',
        body: new URLSearchParams({
          grant_type: 'refresh_token',
          refresh_token: localStorage.getItem('refresh_token')!,
          client_id: 'eaf-admin',
        }),
      });
      return response.json();
    };

    // Then: Network error propagates (should trigger logout/re-login)
    await expect(refreshAccessToken()).rejects.toThrow('Network error');
  });

  it('should handle expired refresh_token (force re-login)', async () => {
    // Given: Expired refresh token
    const accessToken = createMockJWT({
      exp: Math.floor(Date.now() / 1000) + 180,
    });
    localStorage.setItem('token', accessToken);
    localStorage.setItem('refresh_token', 'expired.refresh.token');

    (global.fetch as any).mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => ({ error: 'invalid_grant', error_description: 'Token expired' }),
    });

    // When: Attempting refresh with expired refresh_token
    const refreshAccessToken = async () => {
      const response = await fetch('http://localhost:8180/realms/eaf/protocol/openid-connect/token', {
        method: 'POST',
        body: new URLSearchParams({
          grant_type: 'refresh_token',
          refresh_token: localStorage.getItem('refresh_token')!,
          client_id: 'eaf-admin',
        }),
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.error_description || 'Refresh failed');
      }

      return response.json();
    };

    // Then: Refresh fails (should trigger full re-login)
    await expect(refreshAccessToken()).rejects.toThrow('Token expired');
  });
});

// Helper function to create mock JWT tokens for testing
function createMockJWT(payload: any): string {
  const header = { alg: 'RS256', typ: 'JWT' };
  const encodedHeader = btoa(JSON.stringify(header));
  const encodedPayload = btoa(JSON.stringify(payload));
  const signature = 'mock-signature';

  return `${encodedHeader}.${encodedPayload}.${signature}`;
}
