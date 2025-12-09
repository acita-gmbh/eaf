import {expect, Page, test as setup} from '@playwright/test'
import {log} from '@seontechnologies/playwright-utils/log'

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
 * - TEST_PASSWORD: Password for test users (default: test)
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

const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? 'http://localhost:8080'
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173'
const TEST_PASSWORD = process.env.TEST_PASSWORD ?? 'test'

const adminFile = 'playwright/.auth/admin.json'
const userFile = 'playwright/.auth/user.json'

/**
 * Authenticates a user via Keycloak and saves the session state.
 *
 * @param page - Playwright page instance
 * @param username - Keycloak username
 * @param outputFile - Path to save the storageState
 */
async function authenticateUser(page: Page, username: string, outputFile: string): Promise<void> {
    await log.step('Navigate to app and find login button')
    await page.goto('/')
    const loginButton = page.getByRole('button', {name: /Sign in with Keycloak/i})
    await expect(loginButton).toBeVisible({timeout: 10000})

    await log.step('Click login and redirect to Keycloak')
    await loginButton.click()
    await page.waitForURL(`${KEYCLOAK_URL}/**`, {timeout: 10000})

    await log.step(`Enter credentials for ${username}`)
    await page.getByLabel(/username/i).fill(username)
    await page.getByLabel(/password/i).fill(TEST_PASSWORD)
    await page.getByRole('button', {name: /sign in/i}).click()

    await log.step('Verify redirect back to app')
    // Use predicate function to avoid dynamic RegExp (ESLint security/detect-non-literal-regexp)
    await page.waitForURL((url) => url.href.startsWith(BASE_URL), {timeout: 10000})

    await log.step('Verify authentication succeeded')
    // Use data-testid for stable selector (not affected by UI text changes)
    await expect(page.getByTestId('dashboard-authenticated')).toBeVisible({
        timeout: 10000,
    })

    await log.step('Save authenticated state')
    await page.context().storageState({path: outputFile})
    await log.success(`Session saved to ${outputFile}`)
}

/**
 * Authenticates as admin user and saves session state.
 */
setup('authenticate as admin', async ({page}) => {
    await authenticateUser(page, 'test-admin', adminFile)
})

/**
 * Authenticates as regular user and saves session state.
 */
setup('authenticate as user', async ({page}) => {
    await authenticateUser(page, 'test-user', userFile)
})
