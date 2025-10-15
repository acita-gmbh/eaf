# Keycloak Configuration for EAF Admin Portal

**Story 9.1 - React-Admin Consumer Application**

This document provides step-by-step Keycloak configuration required for the EAF Admin Portal to authenticate and authorize users.

## Overview

The EAF Admin Portal uses **OAuth 2.0 Password Grant** flow (Direct Access Grants) for authentication. This requires specific Keycloak realm, client, and user configuration.

## Prerequisites

- Keycloak running on http://localhost:8180
- Admin credentials: `admin/whiskey`
- Realm: `eaf-test` (imported from docker-compose setup)

## Configuration Steps

### 1. Access Keycloak Admin Console

1. Navigate to: http://localhost:8180/admin
2. Login with: `admin/whiskey`
3. Switch to realm: **eaf-test** (top-left dropdown)

### 2. Create eaf-admin Client

**Clients** → **Create client**:

**General Settings**:
- **Client ID**: `eaf-admin`
- **Name**: EAF Admin Portal
- **Description**: React-Admin portal for EAF administration
- **Client type**: OpenID Connect
- Click **Next**

**Capability config**:
- **Client authentication**: OFF (public client - no client secret required)
- **Authorization**: OFF
- **Authentication flow**:
  - ✅ **Standard flow**: ON (for future authorization code flow)
  - ✅ **Direct access grants**: **ON** ← **CRITICAL FOR PASSWORD GRANT!**
  - ❌ **Implicit flow**: OFF
  - ❌ **Service accounts roles**: OFF
- Click **Next**

**Login settings**:
- **Valid redirect URIs**:
  ```
  http://localhost:5173/*
  http://localhost:5174/*
  http://localhost:3000/*
  ```
- **Valid post logout redirect URIs**: Same as above
- **Web origins**:
  ```
  http://localhost:5173
  http://localhost:5174
  http://localhost:3000
  ```
- Click **Save**

### 3. Add tenant_id Claim Mapper

**Clients** → **eaf-admin** → **Client scopes** tab → **eaf-admin-dedicated** → **Add mapper** → **By configuration** → **Hardcoded claim**:

- **Name**: `eaf-tenant-uuid`
- **Token Claim Name**: `tenant_id`
- **Claim value**: `550e8400-e29b-41d4-a716-446655440000` (valid UUID format)
- **Claim JSON Type**: String
- **Add to ID token**: ON
- **Add to access token**: ON
- **Add to userinfo**: OFF
- Click **Save**

**Why Required**: EAF's 10-layer JWT validation requires `tenant_id` in UUID format for multi-tenant isolation.

### 4. Create Realm Roles

**Realm roles** → **Create role**:

Create these roles:

1. **USER**
   - Name: `USER`
   - Description: `Standard user role`

2. **widget:read**
   - Name: `widget:read`
   - Description: `Widget read permission`

3. **widget:create**
   - Name: `widget:create`
   - Description: `Widget create permission`

4. **widget:update**
   - Name: `widget:update`
   - Description: `Widget update permission`

5. **widget:delete**
   - Name: `widget:delete`
   - Description: `Widget delete permission`

### 5. Create Test User

**Users** → **Add user**:

- **Username**: `testuser`
- **Email**: `testuser@eaf.local`
- **First name**: `Test`
- **Last name**: `User`
- **Email verified**: OFF
- **Enabled**: ON
- Click **Create**

**Set Password** (Credentials tab):
- Click **Set password**
- **Password**: `testuser`
- **Password confirmation**: `testuser`
- **Temporary**: **OFF** ← **MUST BE OFF!**
- Click **Save**

**Assign Roles** (Role mapping tab):
- Click **Assign role**
- **Filter by realm roles**
- Select:
  - ✅ USER
  - ✅ widget:read
  - ✅ widget:create
  - ✅ widget:update
  - ✅ widget:delete
- Click **Assign**

## Verification

### Test Authentication Endpoint

```bash
curl -X POST "http://localhost:8180/realms/eaf-test/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=testuser&password=testuser&grant_type=password&client_id=eaf-admin"
```

**Expected Response** (truncated):
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "token_type": "Bearer",
  "scope": "email profile"
}
```

### Decode JWT to Verify Claims

Visit https://jwt.io and paste the access_token. Verify payload contains:

```json
{
  "iss": "http://localhost:8180/realms/eaf-test",
  "aud": "account",
  "tenant_id": "550e8400-e29b-41d4-a716-446655440000",
  "realm_access": {
    "roles": ["USER", "widget:read", "widget:create", "widget:update", "widget:delete"]
  }
}
```

## Automated Setup Script

For automated setup, use:

```bash
/Users/michael/acci_eaf/scripts/configure-keycloak-story-9.1.sh
```

This script automates all configuration steps above using Keycloak Admin REST API.

## Troubleshooting

### Issue: "invalid_client" Error

**Symptom**: Login fails with `{"error":"invalid_client"}`

**Solutions**:
1. Verify eaf-admin client exists in eaf-test realm
2. Ensure **Direct access grants** is enabled in client settings
3. Check client ID is exactly `eaf-admin` (case-sensitive)

### Issue: "invalid_grant" - Invalid user credentials

**Symptom**: Login fails with `{"error":"invalid_grant","error_description":"Invalid user credentials"}`

**Solutions**:
1. Verify user exists in **eaf-test realm** (not master realm!)
2. Check password is set and **Temporary** flag is OFF
3. Ensure user is **Enabled**

### Issue: "Required claim missing: tenant_id"

**Symptom**: 401 Unauthorized with message about missing tenant_id

**Solution**:
1. Verify tenant_id claim mapper exists in eaf-admin client scopes
2. Check mapper is set to add to access token
3. Ensure claim value is valid UUID format

### Issue: "Invalid issuer" Error

**Symptom**: 401 with issuer validation failure

**Solution**:
1. Verify framework/security JwtLayerValidators.kt has correct EXPECTED_ISSUER
2. Should match Keycloak realm: `http://localhost:8180/realms/eaf-test`

### Issue: "AuthorizationDeniedException"

**Symptom**: 500 or 403 error when accessing widgets

**Solutions**:
1. Verify roles are assigned to user (Role mapping tab)
2. Check JwtAuthenticationConverter is configured in SecurityFilterChain
3. Ensure roles in JWT match @PreAuthorize requirements

## Production Configuration

**Security Notes for Production**:

1. **Change Grant Type**: Use Authorization Code Flow instead of Password Grant
2. **Token Storage**: Replace localStorage with httpOnly cookies
3. **HTTPS Only**: All Keycloak and API URLs must use HTTPS
4. **Dynamic Tenant Mapping**: Replace hardcoded tenant_id with user attribute
5. **Role-Based Mapping**: Use Keycloak groups for role management

**See**: `docs/architecture/security.md` for production security requirements.

---

**Last Updated**: 2025-10-15 (Story 9.1 Implementation)
**Status**: Complete for development environment
