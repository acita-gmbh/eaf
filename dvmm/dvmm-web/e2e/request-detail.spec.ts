import { test, expect } from '@playwright/test'
// log import available: import { log } from '@seontechnologies/playwright-utils/log'

/**
 * E2E tests for the Request Detail page (Story 2.8).
 *
 * These tests verify:
 * - AC-1: Clicking a request card navigates to the detail view
 * - AC-2: Full request details (name, project, size, justification) are shown
 * - AC-3: Timeline shows chronological history with dates and actors
 * - AC-4: Polling fetches updates every 30 seconds (validated via mock/intercept)
 * - AC-5: Loading state is shown during data fetch
 * - AC-6: Error states (not found, generic errors) are displayed appropriately
 * - AC-7: Cancel action works from detail page (for pending requests)
 *
 * ## Running Authenticated Tests
 *
 * Tests marked `test.skip` require authentication setup:
 * 1. Start backend: `./gradlew :dvmm:dvmm-app:bootRun`
 * 2. Run auth setup: `npm run test:e2e -- --project=setup`
 * 3. Run tests: `npm run test:e2e -- --project=chromium-user request-detail.spec.ts`
 *
 * In CI, these tests are skipped by default. Enable them by configuring
 * Keycloak Testcontainer in the CI pipeline. See `e2e/README.md` for details.
 */

test.describe('Request Detail Page @requires-auth', () => {
  // These tests require authentication via Keycloak

  test.skip('AC-1: clicking request card navigates to detail page', async ({ page }) => {
    await page.goto('/requests')

    // Wait for request cards to load
    await expect(page.locator('[data-testid^="request-card-"]').first()).toBeVisible()

    // Get the first request card's ID from the test id
    const firstCard = page.locator('[data-testid^="request-card-"]').first()
    const testId = await firstCard.getAttribute('data-testid')
    const requestId = testId?.replace('request-card-', '') ?? ''

    // Click the card
    await firstCard.click()

    // Should navigate to detail page
    await expect(page).toHaveURL(`/requests/${requestId}`)

    // Detail page should show the request info
    await expect(page.getByTestId('request-detail-page')).toBeVisible()
  })

  test.skip('AC-2: displays full request details', async ({ page }) => {
    // Navigate directly to a known request detail page
    // (In real tests, this ID would come from test data setup)
    await page.goto('/requests/test-request-id')

    // VM name and status should be visible
    await expect(page.getByTestId('request-detail-vm-name')).toBeVisible()
    await expect(page.locator('[data-testid^="status-badge-"]')).toBeVisible()

    // Project name should be visible
    await expect(page.getByTestId('request-detail-project')).toBeVisible()

    // Size specifications should be visible
    await expect(page.getByTestId('request-detail-cpu')).toBeVisible()
    await expect(page.getByTestId('request-detail-memory')).toBeVisible()
    await expect(page.getByTestId('request-detail-disk')).toBeVisible()

    // Business justification should be visible
    await expect(page.getByTestId('request-detail-justification')).toBeVisible()

    // Metadata should be visible
    await expect(page.getByTestId('request-detail-requester')).toBeVisible()
    await expect(page.getByTestId('request-detail-created')).toBeVisible()
  })

  test.skip('AC-3: timeline shows chronological history', async ({ page }) => {
    await page.goto('/requests/test-request-id')

    // Timeline should be visible
    await expect(page.getByTestId('request-timeline')).toBeVisible()

    // At minimum, should have a CREATED event
    await expect(page.getByTestId('timeline-event-created')).toBeVisible()

    // Event should show label
    await expect(page.getByTestId('timeline-event-label').first()).toBeVisible()

    // Event should show timestamp
    await expect(page.getByTestId('timeline-event-time').first()).toBeVisible()
  })

  test.skip('shows timeline with rejection reason', async ({ page }) => {
    // Navigate to a rejected request
    await page.goto('/requests/rejected-request-id')

    // Should have REJECTED event
    await expect(page.getByTestId('timeline-event-rejected')).toBeVisible()

    // Rejection reason should be displayed
    await expect(page.getByTestId('timeline-event-reason')).toBeVisible()
  })

  test.skip('shows timeline with cancellation reason', async ({ page }) => {
    // Navigate to a cancelled request
    await page.goto('/requests/cancelled-request-id')

    // Should have CANCELLED event
    await expect(page.getByTestId('timeline-event-cancelled')).toBeVisible()

    // Cancellation reason should be displayed if provided
    const reasonElement = page.getByTestId('timeline-event-reason')
    if (await reasonElement.isVisible()) {
      await expect(reasonElement).toContainText('Reason:')
    }
  })

  test.skip('back button navigates to My Requests', async ({ page }) => {
    await page.goto('/requests/test-request-id')

    // Click back button
    await page.getByRole('button', { name: /back to my requests/i }).click()

    // Should navigate to My Requests
    await expect(page).toHaveURL('/requests')
  })
})

test.describe('Request Detail Cancel Action @requires-backend', () => {
  test.skip('AC-7: shows cancel button for pending requests', async ({ page }) => {
    // Navigate to a pending request
    await page.goto('/requests/pending-request-id')

    // Cancel button should be visible
    await expect(page.getByTestId('cancel-request-button')).toBeVisible()
  })

  test.skip('does not show cancel button for approved requests', async ({ page }) => {
    // Navigate to an approved request
    await page.goto('/requests/approved-request-id')

    // Cancel button should NOT be visible
    await expect(page.getByTestId('cancel-request-button')).not.toBeVisible()
  })

  test.skip('cancel from detail page updates timeline', async ({ page }) => {
    await page.goto('/requests/pending-request-id')

    // Click cancel button
    await page.getByTestId('cancel-request-button').click()

    // Confirmation dialog should open
    await expect(page.getByTestId('cancel-confirm-dialog')).toBeVisible()

    // Add reason and confirm
    await page.getByTestId('cancel-reason-input').fill('Changed requirements')
    await page.getByTestId('cancel-dialog-confirm').click()

    // Dialog should close
    await expect(page.getByTestId('cancel-confirm-dialog')).not.toBeVisible()

    // Status should update to CANCELLED
    await expect(page.getByTestId('status-badge-cancelled')).toBeVisible()

    // Timeline should show CANCELLED event
    await expect(page.getByTestId('timeline-event-cancelled')).toBeVisible()
  })
})

test.describe('Request Detail Loading & Error States', () => {
  test.skip('AC-5: shows loading state during data fetch', async ({ page }) => {
    // Intercept and delay the API call
    await page.route('**/api/requests/*', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1000))
      await route.continue()
    })

    await page.goto('/requests/test-request-id')

    // Loading state should be visible initially
    await expect(page.getByTestId('request-detail-loading')).toBeVisible()
  })

  test.skip('AC-6: shows not found state for invalid request ID', async ({ page }) => {
    // Navigate to a non-existent request
    await page.goto('/requests/non-existent-id')

    // Not found state should be visible
    await expect(page.getByTestId('request-detail-not-found')).toBeVisible()
    await expect(page.getByText(/request not found/i)).toBeVisible()

    // Should have link back to My Requests
    await expect(page.getByRole('link', { name: /view my requests/i })).toBeVisible()
  })

  test.skip('AC-6: shows error state on API failure', async ({ page }) => {
    // Intercept and fail the API call
    await page.route('**/api/requests/*', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Internal Server Error' }),
      })
    })

    await page.goto('/requests/test-request-id')

    // Error state should be visible
    await expect(page.getByTestId('request-detail-error')).toBeVisible()
    await expect(page.getByText(/error loading request/i)).toBeVisible()

    // Should have retry button
    await expect(page.getByRole('button', { name: /try again/i })).toBeVisible()
  })
})

test.describe('Request Detail Polling @requires-backend', () => {
  test.skip('AC-4: polls for updates at 30 second intervals', async ({ page }) => {
    let requestCount = 0

    // Count API requests
    await page.route('**/api/requests/*', async (route) => {
      requestCount++
      await route.continue()
    })

    await page.goto('/requests/test-request-id')

    // Initial request
    expect(requestCount).toBe(1)

    // Wait slightly more than 30 seconds for the first poll
    // Note: In practice, we'd use fake timers for faster tests
    await page.waitForTimeout(31000)

    // Should have made at least 2 requests (initial + poll)
    expect(requestCount).toBeGreaterThanOrEqual(2)
  })
})

/**
 * Unauthenticated tests - can run without auth.
 */
test.describe('Request Detail Page - Unauthenticated', () => {
  test('redirects to login when accessing detail page without auth', async ({ page }) => {
    await page.goto('/requests/some-request-id')

    // Should see login button (unauthenticated state)
    await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible({
      timeout: 10000,
    })
  })
})

/**
 * Navigation accessibility tests.
 */
test.describe('Request Detail Accessibility', () => {
  test.skip('card navigation is keyboard accessible', async ({ page }) => {
    await page.goto('/requests')

    // Wait for request cards to load
    await expect(page.locator('[data-testid^="request-card-"]').first()).toBeVisible()

    // Tab to the first card
    await page.keyboard.press('Tab')

    // The card should be focusable (has tabIndex="0")
    const firstCard = page.locator('[data-testid^="request-card-"]').first()
    await expect(firstCard).toBeFocused()

    // Press Enter to navigate
    await page.keyboard.press('Enter')

    // Should navigate to detail page
    await expect(page.getByTestId('request-detail-page')).toBeVisible()
  })
})
