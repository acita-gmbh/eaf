import { test as setup, expect } from '@playwright/test'

/**
 * Authentication setup for E2E tests.
 *
 * This script authenticates users and saves their auth state to storageState files.
 * Other tests can then reuse these authenticated states without re-authenticating.
 *
 * Prerequisites:
 * - Backend API must be running with Keycloak authentication
 * - Keycloak must have test users configured (see test-realm.json)
 *
 * Environment variables:
 * - BASE_URL: Frontend application URL (default: http://localhost:5173)
 * - KEYCLOAK_URL: Keycloak server URL (default: http://localhost:8080)
 * - API_URL: Backend API URL (default: http://localhost:8081)
 *
 * Test users (from test-realm.json):
 * - test-admin / test (tenant1, admin role)
 * - test-user / test (tenant1, user role)
 * - tenant2-user / test (tenant2, user role)
 *
 * Usage in tests:
 * ```ts
 * test.use({ storageState: 'playwright/.auth/admin.json' })
 * ```
 */

const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'http://localhost:8080'
const API_URL = process.env.API_URL || 'http://localhost:8081'
const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'

const adminFile = 'playwright/.auth/admin.json'
const userFile = 'playwright/.auth/user.json'

/**
 * Authenticates as admin user and saves session state.
 */
setup('authenticate as admin', async ({ page }) => {
  // Navigate to the app
  await page.goto('/')

  // Wait for the login button to appear
  const loginButton = page.getByRole('button', { name: /Sign in with Keycloak/i })
  await expect(loginButton).toBeVisible({ timeout: 10000 })

  // Click the login button - this should redirect to Keycloak
  await loginButton.click()

  // Wait for Keycloak login page
  await page.waitForURL(`${KEYCLOAK_URL}/**`, { timeout: 10000 })

  // Fill in credentials
  await page.getByLabel(/username/i).fill('test-admin')
  await page.getByLabel(/password/i).fill('test')

  // Submit the form
  await page.getByRole('button', { name: /sign in/i }).click()

  // Wait for redirect back to app
  await page.waitForURL(new RegExp(`^${BASE_URL.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`), {
    timeout: 10000,
  })

  // Verify authentication succeeded
  await expect(page.getByRole('heading', { name: /My Virtual Machines/i })).toBeVisible({
    timeout: 10000,
  })

  // Save the authenticated state
  await page.context().storageState({ path: adminFile })
})

/**
 * Authenticates as regular user and saves session state.
 */
setup('authenticate as user', async ({ page }) => {
  // Navigate to the app
  await page.goto('/')

  // Wait for the login button to appear
  const loginButton = page.getByRole('button', { name: /Sign in with Keycloak/i })
  await expect(loginButton).toBeVisible({ timeout: 10000 })

  // Click the login button - this should redirect to Keycloak
  await loginButton.click()

  // Wait for Keycloak login page
  await page.waitForURL(`${KEYCLOAK_URL}/**`, { timeout: 10000 })

  // Fill in credentials
  await page.getByLabel(/username/i).fill('test-user')
  await page.getByLabel(/password/i).fill('test')

  // Submit the form
  await page.getByRole('button', { name: /sign in/i }).click()

  // Wait for redirect back to app
  await page.waitForURL(new RegExp(`^${BASE_URL.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`), {
    timeout: 10000,
  })

  // Verify authentication succeeded
  await expect(page.getByRole('heading', { name: /My Virtual Machines/i })).toBeVisible({
    timeout: 10000,
  })

  // Save the authenticated state
  await page.context().storageState({ path: userFile })
})
