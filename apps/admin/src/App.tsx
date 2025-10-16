import React from 'react';
import { AdminShell } from '@axians/eaf-admin-shell';
import { widgetResource } from '@eaf/product-widget-demo-ui';

/**
 * EAF Admin Portal - Consumer Application
 *
 * Architecture: Micro-frontend (Hybrid Option 3)
 * - Framework shell: @axians/eaf-admin-shell (infrastructure)
 * - Product UI modules: @eaf/product-widget-demo-ui (domain-specific)
 * - Consumer app: Dynamic resource registration
 *
 * Story: 9.1 - Implement React-Admin Consumer Application
 * Phase 2: Initialize Consumer App (AC 7-12)
 * Phase 4: Keycloak Authentication Integration (AC 16-19)
 */
const App: React.FC = () => {
  return (
    <AdminShell
      resources={[widgetResource]}
      apiBaseUrl={import.meta.env.VITE_API_URL ?? 'http://localhost:8080'}
      keycloakConfig={{
        realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'eaf',
        clientId: import.meta.env.VITE_KEYCLOAK_CLIENT ?? 'eaf-admin',
        serverUrl: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
      }}
    />
  );
};

export default App;
