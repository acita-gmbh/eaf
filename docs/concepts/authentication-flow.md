# Authentication Flow

**Securing the application with OpenID Connect and Keycloak.**

DVMM uses **Keycloak** as its Identity Provider (IdP). We verify identity using standard **OpenID Connect (OIDC)** and Authorization Code Flow. This ensures that we never handle user passwords directly and allows for features like Single Sign-On (SSO) and Multi-Factor Authentication (MFA).

---

## The Flow Overview

```mermaid
sequenceDiagram
    participant User
    participant Frontend as React Frontend
    participant Keycloak as Keycloak (IdP)
    participant API as DVMM Backend

    User->>Frontend: Clicks "Login"
    Frontend->>Keycloak: Redirects to Login Page
    User->>Keycloak: Enters Credentials
    Keycloak->>Frontend: Redirects back with Auth Code
    Frontend->>Keycloak: Exchanges Code for Tokens
    Keycloak->>Frontend: Returns Access & ID Tokens
    Frontend->>API: Request + Bearer Token
    API->>API: Validates Token & Tenant
    API->>Frontend: Returns Data
```

### 1. Login (Frontend)

1.  The user clicks "Login".
2.  The React app (using `react-oidc-context`) redirects the browser to the Keycloak login page.
3.  The user authenticates (username/password, MFA, etc.).
4.  Keycloak redirects back to the React app with a temporary **Authorization Code**.
5.  The React app exchanges this code for an **Access Token** (JWT) and an **ID Token**.

### 2. API Request (Frontend → Backend)

When the frontend calls the API, it attaches the Access Token to the `Authorization` header:

```http
GET /api/v1/projects HTTP/1.1
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI...
```

### 3. Validation (Backend)

The Spring Boot backend intercepts every request.

1.  **Signature Check:** Verifies that the token was signed by our Keycloak instance (using the public key).
2.  **Expiration Check:** Ensures the token hasn't expired.
3.  **Tenant Extraction:** Extracts the `tenant_id` claim from the JWT.
4.  **Role Extraction:** Maps Keycloak roles (`realm_access.roles`) to application authorities.

### 4. Multi-Tenancy (The Critical Part)

DVMM is multi-tenant. This means data for "Tenant A" and "Tenant B" lives in the same database but must *never* mix.

**Security Chain:**

1.  **JWT Claim:** The token contains `tenant_id: "uuid-of-tenant-a"`.
2.  **Security Context:** The backend extracts this ID and places it in the **Reactive Security Context**.
3.  **RLS Policy:** Before executing any SQL, the application sets a PostgreSQL session variable: `SET app.tenant_id = 'uuid-of-tenant-a'`.
4.  **Database Enforcement:** PostgreSQL Row-Level Security (RLS) policies automatically filter every query. If the query asks for `SELECT * FROM vm_requests`, the DB implicitly adds `WHERE tenant_id = 'uuid-of-tenant-a'`.

**Fail-Closed Design:** If a request arrives without a valid `tenant_id`, the database connection rejects all queries.

---

## Key Components

### Frontend (`dvmm-web`)
*   **Library:** `react-oidc-context`
*   **Config:** Connects to Keycloak realm `dvmm`.
*   **Behavior:** Automatically refreshes tokens before they expire.

### Identity Provider (Keycloak)
*   **Realm:** `dvmm`
*   **Client:** `dvmm-web` (Public Client)
*   **Users:** Managed in Keycloak. Custom attributes store the `tenant_id`.

### Backend (`eaf-auth`, `eaf-auth-keycloak`)
*   **Resource Server:** Configured as an OAuth2 Resource Server.
*   **JWT Decoder:** Validates signatures.
*   **Tenant Filter:** Intercepts requests to set up the execution context.

## For Developers

### Local Development Credentials

When running locally (`docker compose up`), Keycloak is pre-configured with a test realm.

*   **Admin Console:** `http://localhost:8180`
*   **User:** `admin` / `admin`
*   **Test Users:** Defined in `eaf/eaf-testing/src/main/resources/test-realm.json`.

### Testing Auth
You don't need to click through a login screen for integration tests. We use **Testcontainers** to spin up a real Keycloak instance and obtain valid tokens programmatically for our test requests.
