import { jwtDecode } from 'jwt-decode';
import type { JWTPayload, ProblemDetails } from '../types';

/**
 * Extract tenant ID from JWT token
 * @param token - JWT token string
 * @returns Tenant ID or null if missing
 */
export function extractTenantFromJWT(token: string | null): string | null {
  if (!token) return null;

  try {
    const decoded = jwtDecode<JWTPayload>(token);
    return decoded.tenant_id || null;
  } catch (error) {
    console.error('Failed to decode JWT:', error);
    return null;
  }
}

/**
 * Parse RFC 7807 Problem Details from API error response
 * @param error - Error object from fetch/axios (unknown type for safety)
 * @returns Parsed error details with message and caption
 */
export function parseRFC7807Error(error: unknown): { message: string; caption?: string } {
  // Type guard for error with body property
  const hasBody = (err: unknown): err is { body?: { type?: string } } =>
    typeof err === 'object' && err !== null && 'body' in err;

  // Check if error has RFC 7807 structure
  if (hasBody(error) && error.body?.type?.includes('/errors/')) {
    const problemDetails = error.body as ProblemDetails;

    return {
      message: problemDetails.detail || problemDetails.title || 'Operation failed',
      caption: problemDetails.traceId
        ? `Error ID: ${problemDetails.traceId} (copy for support)`
        : undefined,
    };
  }

  // Type guard for Error instance
  const hasMessage = (err: unknown): err is { message: string } =>
    typeof err === 'object' && err !== null && 'message' in err;

  // Fallback for non-RFC 7807 errors
  return {
    message: hasMessage(error) ? error.message : 'An unexpected error occurred. Please try again.',
  };
}

/**
 * Check if JWT token is expired
 * @param token - JWT token string
 * @returns True if token is expired or invalid
 */
export function isTokenExpired(token: string | null): boolean {
  if (!token) return true;

  try {
    const decoded = jwtDecode<JWTPayload>(token);
    const expiresIn = decoded.exp * 1000 - Date.now();
    return expiresIn <= 0;
  } catch (error) {
    return true; // Invalid token = expired
  }
}

/**
 * Get remaining time until token expiration in milliseconds
 * @param token - JWT token string
 * @returns Milliseconds until expiration, or 0 if expired/invalid
 */
export function getTokenExpiresIn(token: string | null): number {
  if (!token) return 0;

  try {
    const decoded = jwtDecode<JWTPayload>(token);
    const expiresIn = decoded.exp * 1000 - Date.now();
    return Math.max(0, expiresIn);
  } catch (error) {
    return 0;
  }
}
