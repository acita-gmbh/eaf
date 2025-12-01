// TODO: Migrate to @seontechnologies/playwright-utils fixtures (apiRequest, recurse, log)
// when moduleResolution is updated to support ESM exports from the package.
// See: https://github.com/acita-gmbh/eaf/pull/52#discussion (CodeRabbit suggestion)
// Tracking: Story TBD - E2E test infrastructure improvements
import { test, expect } from '@playwright/test'

/**
 * E2E tests for the My Requests page.
 *
 * These tests verify:
 * - AC 1: Paginated list displays VM requests with required columns
 * - AC 2: Status badge shows correct color per status
 * - AC 3: Cancel button visible only for PENDING status
 * - AC 4: Cancel confirmation dialog with optional reason
 * - AC 5: Cancelled requests reflect updated status
 *
 * Full E2E tests with API calls require a running backend and Keycloak.
 *
 * ## Why most tests are marked `test.skip`
 *
 * Tests tagged with `@requires-auth` or `@requires-backend` are skipped because:
 * 1. Keycloak authentication integration for E2E is not yet configured in the CI pipeline
 * 2. These tests require a running backend with seeded test data
 * 3. The test scenarios are written and ready to enable once auth E2E setup is complete
 *
 * The test logic is validated through:
 * - Integration tests for API functions (vm-requests.test.ts)
 * - The unauthenticated redirect test runs without auth
 * - Component-level behavior verified by API and component structure
 *
 * TODO: Enable these tests when Playwright auth configuration is added (Story TBD).
 */

test.describe('My Requests Page @requires-auth', () => {
  // These tests require authentication via Keycloak

  test.skip('displays page header and empty state when no requests', async ({ page }) => {
    await page.goto('/requests')

    // Page header should be visible
    await expect(page.getByRole('heading', { name: /my requests/i })).toBeVisible()
    await expect(page.getByText(/overview of your vm requests/i)).toBeVisible()

    // CTA button should be visible
    await expect(page.getByRole('button', { name: /request new vm/i })).toBeVisible()

    // Empty state should be shown
    await expect(page.getByText(/no requests/i)).toBeVisible()
    await expect(page.getByText(/request new vm/i)).toBeVisible()
  })

  test.skip('displays request cards with all required information', async ({ page }) => {
    // This test requires existing request data
    await page.goto('/requests')

    // Wait for request cards to load
    await expect(page.getByTestId('requests-list')).toBeVisible()

    // First request card should have all elements
    const firstCard = page.locator('[data-testid^="request-card-"]').first()
    await expect(firstCard.getByTestId('request-vm-name')).toBeVisible()
    await expect(firstCard.getByTestId('request-project')).toBeVisible()
    await expect(firstCard.getByTestId('request-cpu')).toBeVisible()
    await expect(firstCard.getByTestId('request-memory')).toBeVisible()
    await expect(firstCard.getByTestId('request-disk')).toBeVisible()
    await expect(firstCard.getByTestId('request-created-at')).toBeVisible()
  })

  test.skip('shows status badges with correct colors', async ({ page }) => {
    await page.goto('/requests')

    // Test PENDING badge (yellow)
    const pendingBadge = page.getByTestId('status-badge-pending').first()
    await expect(pendingBadge).toBeVisible()
    await expect(pendingBadge).toHaveText('Pending')

    // Status badge styling is enforced via Tailwind classes
    // Visual verification would require screenshot comparison
  })

  test.skip('shows cancel button only for PENDING requests', async ({ page }) => {
    await page.goto('/requests')

    // Find a PENDING request card
    const pendingCard = page.locator('[data-testid^="request-card-"]', {
      has: page.getByTestId('status-badge-pending'),
    }).first()

    // Should have cancel button
    await expect(pendingCard.getByTestId('cancel-request-button')).toBeVisible()

    // Find a non-PENDING request card (if any exist)
    const approvedCard = page.locator('[data-testid^="request-card-"]', {
      has: page.getByTestId('status-badge-approved'),
    }).first()

    // Should NOT have cancel button
    if (await approvedCard.count() > 0) {
      await expect(approvedCard.getByTestId('cancel-request-button')).not.toBeVisible()
    }
  })
})

test.describe('My Requests Cancel Flow @requires-backend', () => {
  // These tests require full backend stack

  test.skip('opens cancel confirmation dialog', async ({ page }) => {
    await page.goto('/requests')

    // Find and click cancel button on first pending request
    const cancelButton = page.getByTestId('cancel-request-button').first()
    await cancelButton.click()

    // Dialog should open
    const dialog = page.getByTestId('cancel-confirm-dialog')
    await expect(dialog).toBeVisible()

    // Dialog should have title and description
    await expect(dialog.getByText(/cancel request/i)).toBeVisible()
    await expect(dialog.getByText(/are you sure you want to cancel/i)).toBeVisible()

    // Should have reason input
    await expect(dialog.getByTestId('cancel-reason-input')).toBeVisible()

    // Should have cancel and confirm buttons
    await expect(dialog.getByTestId('cancel-dialog-cancel')).toBeVisible()
    await expect(dialog.getByTestId('cancel-dialog-confirm')).toBeVisible()
  })

  test.skip('cancels dialog without cancelling request', async ({ page }) => {
    await page.goto('/requests')

    // Open cancel dialog
    await page.getByTestId('cancel-request-button').first().click()

    // Click cancel button in dialog
    await page.getByTestId('cancel-dialog-cancel').click()

    // Dialog should close
    await expect(page.getByTestId('cancel-confirm-dialog')).not.toBeVisible()

    // Request should still be PENDING
    await expect(page.getByTestId('status-badge-pending').first()).toBeVisible()
  })

  test.skip('successfully cancels a pending request', async ({ page }) => {
    await page.goto('/requests')

    // Count pending requests before
    const pendingCountBefore = await page.getByTestId('status-badge-pending').count()

    // Open cancel dialog
    await page.getByTestId('cancel-request-button').first().click()

    // Optionally add a reason
    await page.getByTestId('cancel-reason-input').fill('No longer needed')

    // Confirm cancellation
    await page.getByTestId('cancel-dialog-confirm').click()

    // Wait for dialog to close
    await expect(page.getByTestId('cancel-confirm-dialog')).not.toBeVisible()

    // The cancelled request should now show CANCELLED status
    await expect(page.getByTestId('status-badge-cancelled').first()).toBeVisible()

    // Should have one fewer pending request
    const pendingCountAfter = await page.getByTestId('status-badge-pending').count()
    expect(pendingCountAfter).toBe(pendingCountBefore - 1)
  })

  test.skip('shows loading state during cancellation', async ({ page }) => {
    await page.goto('/requests')

    // Open cancel dialog
    await page.getByTestId('cancel-request-button').first().click()

    // Intercept the cancel API call to slow it down
    await page.route('**/api/requests/*/cancel', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1000))
      await route.continue()
    })

    // Click confirm and check for loading state
    await page.getByTestId('cancel-dialog-confirm').click()

    // Button should show loading text
    await expect(page.getByText(/cancelling/i)).toBeVisible()
  })
})

test.describe('My Requests Pagination @requires-auth', () => {
  // These tests require multiple requests to test pagination

  test.skip('displays page size selector with options', async ({ page }) => {
    await page.goto('/requests')

    // Page size selector should be visible
    const pageSizeSelector = page.getByTestId('page-size-selector')
    await expect(pageSizeSelector).toBeVisible()

    // Open selector
    await pageSizeSelector.click()

    // Should have 10, 25, 50 options
    await expect(page.getByRole('option', { name: '10' })).toBeVisible()
    await expect(page.getByRole('option', { name: '25' })).toBeVisible()
    await expect(page.getByRole('option', { name: '50' })).toBeVisible()
  })

  test.skip('changes page size and reloads data', async ({ page }) => {
    await page.goto('/requests')

    // Change page size to 25
    await page.getByTestId('page-size-selector').click()
    await page.getByRole('option', { name: '25' }).click()

    // Should show up to 25 items (if that many exist)
    const requestCards = page.locator('[data-testid^="request-card-"]')
    await expect(requestCards).toHaveCount(expect.any(Number))
  })

  test.skip('navigates between pages', async ({ page }) => {
    await page.goto('/requests')

    // Wait for pagination to be visible (indicates multiple pages)
    const pagination = page.getByTestId('pagination')

    if (await pagination.isVisible()) {
      // Click next page
      await page.getByRole('link', { name: /next/i }).click()

      // Should show page 2
      await expect(page.getByTestId('page-2')).toHaveAttribute('aria-current', 'page')

      // Go back to page 1
      await page.getByRole('link', { name: /previous/i }).click()

      // Should show page 1
      await expect(page.getByTestId('page-1')).toHaveAttribute('aria-current', 'page')
    }
  })
})

/**
 * Unauthenticated tests - can run without auth.
 */
test.describe('My Requests Page - Unauthenticated', () => {
  test('redirects to login when accessing /requests without auth', async ({ page }) => {
    await page.goto('/requests')

    // Should see login button (unauthenticated state)
    await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible({
      timeout: 10000,
    })
  })
})

/**
 * Navigation tests - can run without auth to verify route exists.
 */
test.describe('My Requests Navigation', () => {
  // This test requires authentication because unauthenticated users see the login screen,
  // not the sidebar navigation. Marking as skip until auth E2E setup is complete.
  test.skip('sidebar shows "My Requests" navigation link', async ({ page }) => {
    await page.goto('/')

    // After authentication, verify the My Requests link is in the sidebar
    await expect(page.getByRole('link', { name: /my requests/i })).toBeVisible()
  })
})
