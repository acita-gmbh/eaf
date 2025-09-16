# Data Models

### Widget

  * **Purpose:** The 'Widget' is the simple, test aggregate used to validate the end-to-end "Walking Skeleton" flow (PRD Epic 2).
  * **Key Attributes:** `widgetId: UUID`, `name: String`, `tenantId: UUID`.
  * **TypeScript Interface (Code-Gen):** `WidgetProjection { widgetId, tenantId, name, createdDate, lastModifiedDate }`.
  * **Relationships:** Belongs to one (1) Tenant.

### Tenant (Revision 2)

  * **Purpose:** The 'Tenant' aggregate is the root entity for all data isolation (PRD Epic 4), representing a customer organization. Manages the tenant's business lifecycle (Active/Suspended).
  * **Key Attributes:** `tenantId: UUID`, `name: String`, `status: Enum`, **`keycloakRealmOrGroupId: String`** (The critical link to the IAM system).
  * **TypeScript Interface (Code-Gen):** `TenantProjection { tenantId, name, status, keycloakRealmOrGroupId, createdDate }`.
  * **Relationships:** Root entity. External 1:1 mapping to Keycloak entity.

### Product

  * **Purpose:** The 'Product' aggregate (PRD Epic 8.2). Represents software that can be licensed (e.g., 'DPCM'). This data is owned by the internal "Admin Tenant".
  * **Key Attributes:** `productId: UUID`, `tenantId: UUID` (Admin Owner), `sku: String`, `name: String`.
  * **TypeScript Interface (Code-Gen):** `ProductProjection { productId, tenantId, sku, name, description?, createdDate }`.
  * **Relationships:** Owned by Admin Tenant. 1:M relationship *with* License.

### License (Revision 2)

  * **Purpose:** The core business entity (PRD Epic 8.3). Represents a customer Tenant's permission to use a Product.
  * **Key Attributes:** `licenseId: UUID`, `tenantId: UUID` (Customer Tenant Owner - the RLS key), `productId: UUID` (Reference to Product), `licenseKey: String`, `status: Enum`, `expires: Date`, **`licenseLimits: Map<String, String>`** (JSONB field for dynamic limits, e.g., {"cpu_cores": "8"}).
  * **TypeScript Interface (Code-Gen):** `LicenseProjection { licenseId, tenantId, productId, productName?, status, expires, licenseLimits: Record<string, string> }`.
  * **Relationships:** Belongs to one (1) Customer Tenant (RLS boundary). Belongs to one (1) Product.

-----
