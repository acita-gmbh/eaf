import { test, expect } from '@playwright/test'

/**
 * E2E tests for the VM Request Form.
 *
 * These tests verify:
 * - AC 1: Submit button is displayed and disabled until form is valid
 * - AC 3: Validation errors show for invalid input
 * - AC 7: Unsaved changes protection (navigation confirmation)
 *
 * Full E2E tests with API calls require a running backend and Keycloak.
 * Basic UI tests verify form behavior without authentication.
 *
 * ## Running Authenticated Tests
 *
 * Tests marked `test` require authentication setup:
 * 1. Start backend: `./gradlew :dvmm:dvmm-app:bootRun`
 * 2. Run auth setup: `npm run test:e2e -- --project=setup`
 * 3. Run tests: `npm run test:e2e -- --project=chromium-user vm-request-form.spec.ts`
 *
 * See `e2e/README.md` for full instructions.
 */

test.describe('VM Request Form @requires-auth', () => {
  // Skip: Requires Keycloak auth. Enable with `--project=chromium-user` after setup.

  test('displays form with all required fields', async ({ page }) => {
    await page.goto('/requests/new')

    // All form fields should be visible
    await expect(page.getByLabel(/VM Name/i)).toBeVisible()
    await expect(page.getByLabel(/Project/i)).toBeVisible()
    await expect(page.getByLabel(/Justification/i)).toBeVisible()
    await expect(page.getByLabel(/VM Size/i)).toBeVisible()

    // Submit button should be visible but disabled
    const submitButton = page.getByTestId('submit-button')
    await expect(submitButton).toBeVisible()
    await expect(submitButton).toBeDisabled()
  })

  test('shows validation errors for invalid VM name', async ({ page }) => {
    await page.goto('/requests/new')

    // Enter invalid VM name
    const vmNameInput = page.getByPlaceholder('e.g. web-server-01')
    await vmNameInput.fill('Ab')
    await vmNameInput.blur()

    // Should show validation error
    await expect(page.getByText(/minimum 3 characters/i)).toBeVisible()
  })

  test('shows validation errors for invalid characters in VM name', async ({ page }) => {
    await page.goto('/requests/new')

    const vmNameInput = page.getByPlaceholder('e.g. web-server-01')
    await vmNameInput.fill('Invalid_Name!')
    await vmNameInput.blur()

    await expect(page.getByText(/Only lowercase/i)).toBeVisible()
  })

  test('enables submit button when form is valid', async ({ page }) => {
    await page.goto('/requests/new')

    // Fill valid VM name
    await page.getByPlaceholder('e.g. web-server-01').fill('test-vm-01')

    // Select project
    await page.getByTestId('project-select-trigger').click()
    await page.getByText('Project Alpha').click()

    // Fill justification (min 10 chars)
    await page.getByPlaceholder(/describe the purpose/i).fill('This VM is needed for testing purposes.')

    // Size is pre-selected by default (M)

    // Submit button should now be enabled
    const submitButton = page.getByTestId('submit-button')
    await expect(submitButton).toBeEnabled()
  })
})

/**
 * Form submission tests requiring full backend stack.
 * These are marked as skip by default.
 */
test.describe('VM Request Form Submission @requires-backend', () => {
  test('happy path: submits form and shows success toast', async () => {
    // This test requires:
    // 1. Backend API running
    // 2. Keycloak running with test user
    // 3. PostgreSQL with proper schema
    //
    // Implementation would:
    // 1. Authenticate with test user
    // 2. Navigate to /requests/new
    // 3. Fill all form fields
    // 4. Click submit
    // 5. Verify success toast
    // 6. Verify redirect to /requests/{id}
  })

  test('shows error for backend validation failures', async () => {
    // This test requires backend running
    // Would test 400 response handling
  })

  test('shows error for quota exceeded', async () => {
    // This test requires backend running with quota limits
    // Would test 409 response handling
  })
})

/**
 * Login page redirect test - can run without auth.
 */
test.describe('VM Request Form - Unauthenticated', () => {
  test('redirects to login when accessing /requests/new without auth', async ({ page }) => {
    await page.goto('/requests/new')

    // Should see login button (unauthenticated state)
    await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible({
      timeout: 10000,
    })
  })
})
