import type { ProblemDetails } from '../types';

/**
 * @deprecated SECURITY VULNERABILITY (VULN-001): Do NOT use - JWT signature not validated
 *
 * This function uses jwt-decode which performs BASE64 decoding ONLY.
 * It does NOT validate JWT cryptographic signatures, allowing forged tokens.
 *
 * CRITICAL: Frontend must NOT make security decisions on unverified JWT claims.
 *
 * Correct approach:
 * - Backend validates JWT signature (Epic 3: 10-layer validation)
 * - Backend extracts tenant_id and enforces isolation (Epic 4: 3-layer enforcement)
 * - Frontend gets validated tenant_id from backend API response
 *
 * @param token - JWT token string
 * @returns Always returns null (function disabled for security)
 */
export function extractTenantFromJWT(_token: string | null): string | null {
  console.warn('[SECURITY] extractTenantFromJWT is deprecated - use backend API for tenant validation');
  return null; // Always return null - force backend validation
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
 * @deprecated SECURITY VULNERABILITY (VULN-001): JWT signature not validated
 *
 * Checking expiration on unverified JWT is unsafe - attacker can set exp to far future.
 * Backend checkAuth validates token expiration after signature verification.
 *
 * @param token - JWT token string
 * @returns Always true (force backend validation)
 */
export function isTokenExpired(_token: string | null): boolean {
  console.warn('[SECURITY] isTokenExpired is deprecated - backend validates expiration');
  return true; // Always expired - force backend checkAuth
}

/**
 * @deprecated SECURITY VULNERABILITY (VULN-001): JWT signature not validated
 *
 * Computing expiration time from unverified JWT is unsafe.
 * Backend manages token lifecycle and expiration.
 *
 * @param token - JWT token string
 * @returns Always 0 (expired - force backend validation)
 */
export function getTokenExpiresIn(_token: string | null): number {
  console.warn('[SECURITY] getTokenExpiresIn is deprecated - backend manages token lifecycle');
  return 0; // Always expired - force backend checkAuth
}
