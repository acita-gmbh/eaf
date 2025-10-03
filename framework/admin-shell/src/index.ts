/**
 * @axians/eaf-admin-shell
 *
 * Enterprise Application Framework - React-Admin Shell Infrastructure
 *
 * This package provides reusable React-Admin infrastructure for EAF product modules.
 * It implements Story 4.5's framework/product separation principle for the frontend.
 *
 * Framework Responsibilities (this package):
 * - Authentication (Keycloak OIDC)
 * - Data provider (JWT, tenant injection, RFC 7807 error mapping)
 * - Theming (Axians branding)
 * - Shared UI components
 *
 * Product Responsibilities (consuming packages):
 * - Domain-specific CRUD resources
 * - Business logic
 * - Product-specific UI components
 *
 * @packageDocumentation
 */

// Main shell component
export { AdminShell } from './AdminShell';

// Type exports
export type {
  AdminShellProps,
  ResourceConfig,
  KeycloakConfig,
  JWTPayload,
  ProblemDetails,
} from './types';

// Provider factory functions
export { createDataProvider } from './providers/dataProvider';
export { createAuthProvider } from './providers/authProvider';

// Theme
export { eafTheme } from './theme/theme';

// Shared UI Components
export {
  EmptyState,
  LoadingSkeleton,
  BulkDeleteWithConfirm,
  TypeToConfirmDelete,
} from './components';

export type {
  EmptyStateProps,
  LoadingSkeletonProps,
  TypeToConfirmDeleteProps,
} from './components';

// Utility functions
export { extractTenantFromJWT, parseRFC7807Error, isTokenExpired, getTokenExpiresIn } from './utils';
