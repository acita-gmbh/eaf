# External APIs

### Keycloak Identity and Admin API

  * **Purpose:** (1) OIDC/AuthN validation for the EAF (Epic 3). (2) Programmatic provisioning of tenant realms/groups via the Admin API (Epic 4 / UX Flow 1).
  * **Integration:** Admin API calls (Sagas) MUST be orchestrated via the Flowable Engine (PRD Epic 7) to manage the distributed transaction risk.

### Ansible / SSH Protocol Interface

  * **Purpose:** Allows the Flowable engine (Epic 7.4) to execute automation playbooks (replacing legacy Dockets).
  * **Integration:** This requires replicating the legacy environment (JSON callbacks, custom collections, ENV VARs). This interface faces a **critical API mismatch** (Legacy GraphQL vs. New REST), requiring the legacy collections to be rewritten OR the Post-MVP GraphQL Gateway to be implemented first.

-----
