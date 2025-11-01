import type { ComponentType } from 'react';
import type { Theme } from '@mui/material';

/**
 * Configuration for a single resource in the admin shell
 */
export interface ResourceConfig {
  /** Resource name (e.g., 'products', 'licenses') */
  name: string;
  /** List view component */
  list?: ComponentType;
  /** Create form component */
  create?: ComponentType;
  /** Edit form component */
  edit?: ComponentType;
  /** Detail view component */
  show?: ComponentType;
  /** Icon component (Material-UI icon) */
  icon?: ComponentType;
  /** Additional options (label, etc.) */
  options?: { label?: string };
}

/**
 * Keycloak OIDC configuration
 */
export interface KeycloakConfig {
  /** Keycloak realm name */
  realm: string;
  /** Client ID for this application */
  clientId: string;
  /** Keycloak server URL (e.g., 'http://localhost:8180') */
  serverUrl: string;
}

/**
 * Props for AdminShell component
 */
export interface AdminShellProps {
  /** Array of resource configurations to register */
  resources: ResourceConfig[];
  /** API base URL (default: 'http://localhost:8080/api/v1') */
  apiBaseUrl?: string;
  /** Keycloak configuration */
  keycloakConfig?: KeycloakConfig;
  /** Custom theme override */
  customTheme?: Theme;
}

/**
 * Decoded JWT payload structure
 */
export interface JWTPayload {
  sub: string;
  exp: number;
  iat: number;
  tenant_id?: string;
  realm_access?: {
    roles: string[];
  };
}

/**
 * RFC 7807 Problem Details error structure
 */
export interface ProblemDetails {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  traceId?: string;
  tenantId?: string;
}
