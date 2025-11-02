# Keycloak Configuration for EAF

This directory contains Keycloak realm configuration for the EAF development environment.

## Files

- **realm-export.json** - Complete realm configuration including users, roles, clients, and mappers

## Development Environment

The `realm-export.json` file is pre-configured for local development with:

- **Realm:** eaf
- **Client:** eaf-api (OAuth2 confidential client)
- **Test Users:**
  - `admin` / `admin` (role: WIDGET_ADMIN, tenant: tenant-a)
  - `viewer` / `viewer` (role: WIDGET_VIEWER, tenant: tenant-a)
  - `tenant-b-admin` / `admin` (role: WIDGET_ADMIN, tenant: tenant-b)
- **Roles:** WIDGET_ADMIN, WIDGET_VIEWER, USER
- **Custom Mappers:**
  - `tenant_id` - Maps user attribute to JWT claim (critical for multi-tenancy)
  - `roles` - Maps realm roles to JWT claim
  - `email` - Maps email to JWT claim
  - `username` - Maps username to preferred_username claim

## ⚠️ Security Warning - Production Deployment

**CRITICAL:** The configuration in this file is **FOR DEVELOPMENT ONLY** and contains insecure settings:

### DO NOT Use in Production:

1. **Simple Passwords:** All users have weak passwords ("admin", "viewer")
   - Production: Use strong, randomly generated passwords
   - Enforce password policies (length, complexity, rotation)

2. **Client Secret in Plaintext:** `eaf-api-secret-development-only` is exposed
   - Production: Generate cryptographically strong client secret
   - Store securely in secrets management system
   - **Rotate immediately** when deploying to production

3. **Disabled Security Features:**
   - SSL requirement: "external" (allows HTTP internally)
   - Production: Set `sslRequired: "all"` and enable HTTPS

4. **Development Mode:** Keycloak runs with `start-dev` command
   - Production: Use `start` command with production configuration
   - Enable proper clustering for high availability

### Production Security Checklist:

- [ ] Generate new realm export from production-hardened Keycloak instance
- [ ] Use secrets management (Kubernetes Secrets, HashiCorp Vault, AWS Secrets Manager)
- [ ] Rotate ALL client secrets and user passwords
- [ ] Enable HTTPS/TLS for all connections
- [ ] Configure proper hostname and certificate
- [ ] Enable event logging and audit trails
- [ ] Set up realm-level rate limiting and brute force protection
- [ ] Review and harden token lifespans
- [ ] Disable admin user creation (use dedicated admin realm)
- [ ] Configure proper SMTP for email verification and password reset
- [ ] Review Keycloak Security Hardening Guide

### Resources:

- **Keycloak Security Guide:** https://www.keycloak.org/docs/latest/server_admin/#_hardening
- **Production Deployment:** https://www.keycloak.org/server/configuration-production
- **Container Security:** https://www.keycloak.org/server/containers

## Exporting Realm Configuration

To export realm configuration from running Keycloak:

```bash
# Export realm via Keycloak Admin CLI
docker exec -it eaf-keycloak /opt/keycloak/bin/kc.sh export \
  --realm eaf \
  --file /tmp/realm-export.json \
  --users realm_file

# Copy export from container
docker cp eaf-keycloak:/tmp/realm-export.json ./docker/keycloak/realm-export.json
```

## Importing Realm to Production

**Do NOT directly import this development realm to production!**

Instead:
1. Use development realm as template
2. Create new production realm with secure configuration
3. Generate strong credentials via secrets management
4. Export production realm for disaster recovery (store securely, NOT in git)

## Modifying Realm Configuration

After modifying `realm-export.json`:

1. Restart Keycloak container to import changes:
   ```bash
   docker-compose restart keycloak
   ```

2. Verify realm import in logs:
   ```bash
   docker-compose logs keycloak | grep "imported"
   ```

3. Access Keycloak Admin Console to verify:
   - URL: http://localhost:8080
   - Username: admin
   - Password: admin

## .gitignore Note

The `realm-export.json` file **IS** committed to version control because:
- It contains only development/test credentials
- It's required for automated local environment setup
- Real secrets are environment-specific and injected at runtime

For production:
- **NEVER** commit production realm exports
- Use CI/CD secrets injection
- Store production configurations in secure vaults
