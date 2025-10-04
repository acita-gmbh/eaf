import { useMemo } from 'react';
import { Admin, Resource } from 'react-admin';
import { createDataProvider } from './providers/dataProvider';
import { createAuthProvider } from './providers/authProvider';
import { eafTheme } from './theme/theme';
import type { AdminShellProps } from './types';

/**
 * EAF Admin Shell Component
 *
 * A reusable React-Admin shell that provides:
 * - Dynamic resource registration (plugin architecture)
 * - JWT authentication with Keycloak OIDC
 * - Tenant isolation with automatic X-Tenant-ID injection
 * - RFC 7807 Problem Details error mapping
 * - Axians branded theme
 * - Shared UI components (EmptyState, LoadingSkeleton, etc.)
 *
 * This component implements Story 4.5's framework/product separation principle:
 * - Framework provides INFRASTRUCTURE (auth, theme, data provider)
 * - Products provide DOMAIN RESOURCES (Product, License, Widget UIs)
 *
 * Usage:
 * ```tsx
 * import { AdminShell } from '@axians/eaf-admin-shell';
 * import { productResource, licenseResource } from '@eaf/product-licensing-server-ui';
 *
 * const App = () => (
 *   <AdminShell
 *     resources={[productResource, licenseResource]}
 *     apiBaseUrl="http://localhost:8080/api/v1"
 *     keycloakConfig={{ realm: 'eaf', clientId: 'eaf-admin', serverUrl: 'http://localhost:8180' }}
 *   />
 * );
 * ```
 *
 * @param props - AdminShell configuration
 * @returns Configured React-Admin application
 */
export const AdminShell = ({
  resources,
  apiBaseUrl = 'http://localhost:8080/api/v1',
  keycloakConfig,
  customTheme,
}: AdminShellProps) => {
  // Memoize providers to prevent unnecessary reinitialization on re-renders
  // React-Admin wipes cache and re-authenticates when providers change
  const dataProvider = useMemo(() => createDataProvider(apiBaseUrl), [apiBaseUrl]);
  const authProvider = useMemo(() => createAuthProvider(keycloakConfig), [keycloakConfig]);
  const theme = useMemo(() => customTheme || eafTheme, [customTheme]);

  return (
    <Admin
      dataProvider={dataProvider}
      authProvider={authProvider}
      theme={theme}
      requireAuth
      title="EAF Admin Portal"
    >
      {resources.map((resource) => (
        <Resource
          key={resource.name}
          name={resource.name}
          list={resource.list}
          create={resource.create}
          edit={resource.edit}
          show={resource.show}
          icon={resource.icon}
          options={resource.options}
        />
      ))}
    </Admin>
  );
};
