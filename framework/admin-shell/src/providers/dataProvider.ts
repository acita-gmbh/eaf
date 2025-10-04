import simpleRestProvider from 'ra-data-simple-rest';
import DOMPurify from 'dompurify';
import { extractTenantFromJWT, parseRFC7807Error } from '../utils';
import type { DataProvider } from 'react-admin';

// Scoped localStorage key (matches authProvider)
const TOKEN_STORAGE_KEY = 'eaf.auth.token';

/**
 * Create EAF data provider with JWT authentication, tenant injection, and RFC 7807 error mapping
 *
 * Features:
 * - JWT token injection in Authorization header
 * - Tenant ID extraction from JWT and X-Tenant-ID header injection
 * - DOMPurify sanitization for XSS prevention (SEC-001)
 * - RFC 7807 Problem Details error mapping
 * - Fail-closed tenant validation (SEC-002)
 * - Request/response logging for observability
 *
 * @param apiBaseUrl - Base URL for API (default: http://localhost:8080/api/v1)
 * @returns Configured data provider
 */
export function createDataProvider(apiBaseUrl: string = 'http://localhost:8080/api/v1'): DataProvider {
  const baseProvider = simpleRestProvider(apiBaseUrl, httpClient);

  // Wrap base provider with custom logic
  return {
    ...baseProvider,
    create: async (resource, params) => {
      // Sanitize string inputs to prevent XSS (SEC-001, SEC-004)
      const sanitizedData = sanitizeData(params.data);
      return baseProvider.create(resource, { ...params, data: sanitizedData });
    },
    update: async (resource, params) => {
      // Sanitize string inputs to prevent XSS
      const sanitizedData = sanitizeData(params.data);
      return baseProvider.update(resource, { ...params, data: sanitizedData });
    },
  };
}

/**
 * Custom HTTP client with JWT auth, tenant injection, and error mapping
 * Matches ra-data-simple-rest expected signature
 */
async function httpClient(url: string, options: RequestInit = {}): Promise<{
  status: number;
  headers: Headers;
  body: string;
  json: any;
}> {
  // Get JWT token from localStorage (scoped key)
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);

  // Extract tenant ID from JWT (SEC-002: Tenant context propagation)
  const tenantId = extractTenantFromJWT(token);

  // FAIL-CLOSED: Reject request if tenant ID missing (SEC-002 mitigation)
  if (!tenantId) {
    throw new Error('Tenant context missing - access denied. Please re-login.');
  }

  // Inject authentication and tenant headers
  const headers = new Headers(options.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  headers.set('X-Tenant-ID', tenantId);
  headers.set('Content-Type', 'application/json');

  // Log request for observability (Task 2.5)
  console.log(`[DataProvider] ${options.method || 'GET'} ${url}`, {
    tenantId,
    hasAuth: !!token,
  });

  try {
    // Make API request
    const response = await fetch(url, {
      ...options,
      headers,
    });

    // Log response
    console.log(`[DataProvider] Response ${response.status}`, {
      url,
      status: response.status,
      ok: response.ok,
    });

    // Parse response body
    const text = await response.text();
    const json = text ? JSON.parse(text) : {};

    // Handle errors with RFC 7807 parsing
    if (!response.ok) {
      const parsedError = parseRFC7807Error({ body: json });
      throw new Error(parsedError.message);
    }

    // Return in ra-data-simple-rest expected format
    return {
      status: response.status,
      headers: response.headers,
      body: text,
      json,
    };
  } catch (error: unknown) {
    // Parse RFC 7807 if available, otherwise use generic message
    const parsedError = parseRFC7807Error(error);
    console.error('[DataProvider] Error:', parsedError);

    throw new Error(parsedError.message);
  }
}

/**
 * Sanitize data object to prevent XSS attacks (SEC-001, SEC-004)
 * @param data - Data object with potentially unsafe strings
 * @returns Sanitized data object
 */
function sanitizeData(data: any): any {
  if (typeof data === 'string') {
    return DOMPurify.sanitize(data);
  }

  if (Array.isArray(data)) {
    return data.map((item) => sanitizeData(item));
  }

  if (data && typeof data === 'object') {
    const sanitized: any = {};
    for (const [key, value] of Object.entries(data)) {
      sanitized[key] = sanitizeData(value);
    }
    return sanitized;
  }

  return data;
}
