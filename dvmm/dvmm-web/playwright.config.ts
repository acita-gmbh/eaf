import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright configuration for DVMM Web E2E tests.
 *
 * ## Test Execution Modes
 *
 * ### Without Authentication (Default)
 * Tests without @requires-auth tag run against local dev server:
 * ```bash
 * npm run test:e2e
 * ```
 *
 * ### With Authentication (Full E2E)
 * Tests tagged with @requires-auth require Keycloak authentication setup:
 * ```bash
 * # Start backend with Keycloak
 * ./gradlew :dvmm:dvmm-app:bootRun
 *
 * # Run all tests including authenticated ones
 * npm run test:e2e -- --grep @requires-auth
 * ```
 *
 * ## Authentication Projects
 *
 * Tests can use authenticated sessions by specifying a project:
 * - `setup` - Performs authentication and saves session state
 * - `chromium-admin` - Tests with admin user authentication
 * - `chromium-user` - Tests with regular user authentication
 * - `chromium` - Tests without authentication (default)
 *
 * The setup project must run first to create auth state files.
 *
 * @see https://playwright.dev/docs/test-configuration
 * @see https://playwright.dev/docs/auth for authentication details
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',

  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },

  projects: [
    // Setup project - runs first to create authenticated sessions
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
    },

    // Authenticated admin user tests
    {
      name: 'chromium-admin',
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'playwright/.auth/admin.json',
      },
      dependencies: ['setup'],
    },

    // Authenticated regular user tests
    {
      name: 'chromium-user',
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'playwright/.auth/user.json',
      },
      dependencies: ['setup'],
    },

    // Unauthenticated tests (default)
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      testIgnore: /.*\.setup\.ts/,
    },
  ],

  // Run local dev server before starting tests
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
})
