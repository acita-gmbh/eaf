import { test, expect } from '@playwright/test';
import fs from 'fs';

// Playwright auth storage types
interface LocalStorageItem {
  name: string;
  value: string;
}

interface StorageOrigin {
  origin: string;
  localStorage: LocalStorageItem[];
}

interface AuthStorageState {
  origins: StorageOrigin[];
}

test('debug api connection', async ({ request }) => {
  const health = await request.get('http://127.0.0.1:8080/actuator/health');
  console.log('Health status:', health.status());
  console.log('Health body:', await health.text());
  expect(health.ok()).toBeTruthy();
});

test('create and list requests', async ({ request }) => {
  // Read token from storage state
  const authFile = 'playwright/.auth/admin.json';
  if (!fs.existsSync(authFile)) {
    throw new Error('Auth file not found: ' + authFile);
  }
  const authData: AuthStorageState = JSON.parse(fs.readFileSync(authFile, 'utf-8'));
  const origin = authData.origins.find((o) => o.origin === 'http://127.0.0.1:5173' || o.origin === 'http://localhost:5173');

  if (!origin) {
      console.log('Auth Data:', JSON.stringify(authData, null, 2));
      throw new Error('No origin found in auth file');
  }

  const localStorage = origin.localStorage;
  const oidcKey = localStorage.find((item) => item.name.startsWith('oidc.user'));
  
  if (!oidcKey) {
      console.log('Local Storage:', JSON.stringify(localStorage, null, 2));
      throw new Error('No OIDC user found in local storage');
  }

  const user = JSON.parse(oidcKey.value);
  const token = user.access_token;
  console.log('Token found:', token ? 'YES' : 'NO');

  // Create Request
  console.log('Creating VM request...');
  const createRes = await request.post('http://127.0.0.1:8080/api/requests', {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    data: {
       vmName: `debug-vm-${Date.now()}`,
       projectId: '550e8400-e29b-41d4-a716-446655440000',
       size: 'S',
       justification: 'Debug E2E Write Test'
    }
  });
  
  console.log('Create Status:', createRes.status());
  const createBody = await createRes.text();
  console.log('Create Body:', createBody);
  
  expect(createRes.status(), 'Create request failed').toBe(201);

  // List Requests
  console.log('Listing requests...');
  const response = await request.get('http://127.0.0.1:8080/api/requests/my', {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  console.log('List Status:', response.status());
  const body = await response.json();
  console.log('List Count:', body.items?.length);
  
  expect(response.status()).toBe(200);
  expect(body.items.length).toBeGreaterThan(0);
});
