#!/bin/bash
#
# Keycloak Configuration Script for Story 9.1
# Automates eaf-test realm configuration for React-Admin consumer app
#
# Usage: ./scripts/configure-keycloak-story-9.1.sh
#
# Prerequisites:
# - Keycloak running on http://localhost:8180
# - Admin credentials: admin/whiskey
# - eaf-test realm must exist (created by init-dev.sh)

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}🔧 Keycloak Configuration for Story 9.1${NC}"
echo "=============================================="

# Get admin token
echo "1. Authenticating with Keycloak admin..."
ADMIN_TOKEN=$(curl -s -X POST "http://localhost:8180/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=whiskey&grant_type=password&client_id=admin-cli" | \
  grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo -e "${RED}❌ Failed to authenticate. Check Keycloak credentials.${NC}"
  exit 1
fi
echo -e "${GREEN}✅ Authenticated${NC}"

# Check if eaf-admin client exists
echo "2. Checking for eaf-admin client..."
CLIENT_UUID=$(curl -s "http://localhost:8180/admin/realms/eaf-test/clients?clientId=eaf-admin" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$CLIENT_UUID" ]; then
  echo "   Creating eaf-admin client..."
  curl -s -X POST "http://localhost:8180/admin/realms/eaf-test/clients" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "clientId": "eaf-admin",
      "name": "EAF Admin Portal",
      "description": "React-Admin portal for EAF administration",
      "enabled": true,
      "protocol": "openid-connect",
      "publicClient": true,
      "directAccessGrantsEnabled": true,
      "standardFlowEnabled": true,
      "implicitFlowEnabled": false,
      "serviceAccountsEnabled": false,
      "authorizationServicesEnabled": false,
      "redirectUris": [
        "http://localhost:5173/*",
        "http://localhost:5174/*",
        "http://localhost:3000/*"
      ],
      "webOrigins": [
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:3000"
      ],
      "attributes": {
        "post.logout.redirect.uris": "http://localhost:5173/*##http://localhost:5174/*##http://localhost:3000/*"
      }
    }'

  # Get the newly created client UUID
  CLIENT_UUID=$(curl -s "http://localhost:8180/admin/realms/eaf-test/clients?clientId=eaf-admin" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  echo -e "${GREEN}✅ Client created${NC}"
else
  echo -e "${GREEN}✅ Client already exists${NC}"
fi

# Add tenant_id claim mapper
echo "3. Configuring tenant_id claim mapper..."
curl -s -X POST "http://localhost:8180/admin/realms/eaf-test/clients/$CLIENT_UUID/protocol-mappers/models" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "eaf-tenant-uuid",
    "protocol": "openid-connect",
    "protocolMapper": "oidc-hardcoded-claim-mapper",
    "config": {
      "claim.name": "tenant_id",
      "claim.value": "550e8400-e29b-41d4-a716-446655440000",
      "id.token.claim": "true",
      "access.token.claim": "true",
      "jsonType.label": "String"
    }
  }' > /dev/null 2>&1 || echo "   (mapper may already exist)"
echo -e "${GREEN}✅ tenant_id mapper configured${NC}"

# Create realm roles
echo "4. Creating realm roles..."
for role in "USER" "widget:read" "widget:create" "widget:update" "widget:delete"; do
  curl -s -X POST "http://localhost:8180/admin/realms/eaf-test/roles" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"$role\", \"description\": \"EAF role: $role\"}" > /dev/null 2>&1 || true
  echo "   - $role"
done
echo -e "${GREEN}✅ Roles created${NC}"

# Check if testuser exists
echo "5. Configuring test user..."
USER_ID=$(curl -s "http://localhost:8180/admin/realms/eaf-test/users?username=testuser" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$USER_ID" ]; then
  echo "   Creating testuser..."
  curl -s -X POST "http://localhost:8180/admin/realms/eaf-test/users" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "username": "testuser",
      "email": "testuser@eaf.local",
      "firstName": "Test",
      "lastName": "User",
      "enabled": true,
      "credentials": [{
        "type": "password",
        "value": "testuser",
        "temporary": false
      }]
    }'

  # Get newly created user ID
  USER_ID=$(curl -s "http://localhost:8180/admin/realms/eaf-test/users?username=testuser" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  echo -e "${GREEN}✅ User created${NC}"
else
  echo -e "${GREEN}✅ User already exists${NC}"
fi

# Assign roles to testuser
echo "6. Assigning roles to testuser..."
ROLES_JSON=$(curl -s "http://localhost:8180/admin/realms/eaf-test/roles" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

# Extract role objects and assign
for role_name in "USER" "widget:read" "widget:create" "widget:update" "widget:delete"; do
  ROLE_OBJ=$(echo "$ROLES_JSON" | grep -o "{\"id\":\"[^\"]*\",\"name\":\"$role_name\"[^}]*}" | head -1)
  if [ ! -z "$ROLE_OBJ" ]; then
    curl -s -X POST "http://localhost:8180/admin/realms/eaf-test/users/$USER_ID/role-mappings/realm" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d "[$ROLE_OBJ]" > /dev/null 2>&1 || true
    echo "   - $role_name assigned"
  fi
done
echo -e "${GREEN}✅ Roles assigned${NC}"

echo ""
echo "=============================================="
echo -e "${GREEN}🎉 Keycloak Configuration Complete!${NC}"
echo "=============================================="
echo ""
echo "📋 Configuration Summary:"
echo "  • Realm: eaf-test"
echo "  • Client: eaf-admin (Direct Access Grants enabled)"
echo "  • Test User: testuser/testuser"
echo "  • Roles: USER, widget:read, widget:create, widget:update, widget:delete"
echo "  • Tenant ID: 550e8400-e29b-41d4-a716-446655440000"
echo ""
echo "🧪 Test Authentication:"
echo '  curl -X POST "http://localhost:8180/realms/eaf-test/protocol/openid-connect/token" \'
echo '    -H "Content-Type: application/x-www-form-urlencoded" \'
echo '    -d "username=testuser&password=testuser&grant_type=password&client_id=eaf-admin"'
echo ""
echo "🌐 Access Admin Portal:"
echo "  http://localhost:5173"
echo "  Login: testuser/testuser"
echo ""
