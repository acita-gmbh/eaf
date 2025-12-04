import { test, expect } from '@playwright/test'

/**
 * E2E tests for the Admin Request Detail page.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * These tests verify:
 * - AC 1: Page loads with correct request details
 * - AC 2: Requester Information (name, email, role)
 * - AC 3: Request Details (VM specs, justification)
 * - AC 4: Auto-polling for real-time updates
 * - AC 5: Timeline events displayed
 * - AC 6: Requester History shown (up to 5 recent requests)
 *
 * ## Running Authenticated Tests
 *
 * Tests tagged with `@requires-auth` or `@requires-backend` require:
 * 1. Backend API running with Keycloak authentication
 * 2. Keycloak container with test users configured
 * 3. Auth setup to create authenticated sessions
 *
 * ### Setup Instructions
 *
 * 1. Start the backend with Keycloak:
 * ```bash
 * ./gradlew :dvmm:dvmm-app:bootRun
 * ```
 *
 * 2. Run the auth setup to create authenticated sessions:
 * ```bash
 * cd dvmm/dvmm-web
 * npm run test:e2e -- --project=setup
 * ```
 *
 * 3. Run authenticated tests with the appropriate project:
 * ```bash
 * # Run as admin user
 * npm run test:e2e -- --project=chromium-admin admin-request-detail.spec.ts
 * ```
 *
 * ## Test Coverage Without Full E2E
 *
 * The test scenarios are validated through:
 * - Unit tests for hooks (useAdminRequestDetail.test.ts)
 * - Integration tests for API functions (admin.ts)
 * - Backend integration tests (AdminRequestControllerIntegrationTest)
 * - Component-level tests
 */

test.describe('Admin Request Detail - Access Control @requires-auth', () => {
  test.skip('admin user can access /admin/requests/:id', async ({ page }) => {
    // Navigate to a request detail from the queue
    await page.goto('/admin/requests')

    // Click first row to navigate to detail
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    const firstRow = page.locator('[data-testid^="pending-request-row-"]').first()
    await firstRow.click()

    // Should navigate to admin request detail page
    await expect(page).toHaveURL(/\/admin\/requests\/[\w-]+/)

    // Should see the detail page
    await expect(page.getByTestId('admin-request-detail-page')).toBeVisible()
  })

  test.skip('non-admin user sees access denied on /admin/requests/:id', async ({ page }) => {
    // Direct navigation to an admin request detail page
    await page.goto('/admin/requests/test-uuid-123')

    // Should see access denied page
    await expect(page.getByTestId('admin-forbidden')).toBeVisible()
    await expect(page.getByRole('heading', { name: /access denied/i })).toBeVisible()
  })
})

test.describe('Admin Request Detail - Page Content (AC 1) @requires-auth @requires-backend', () => {
  test.skip('displays VM name and status in header', async ({ page }) => {
    // Navigate to request detail
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Header should show VM name
    await expect(page.getByTestId('admin-request-detail-vm-name')).toBeVisible()

    // Status badge should be visible
    await expect(page.locator('[data-testid^="status-badge-"]')).toBeVisible()

    // Project name should be visible
    await expect(page.getByTestId('admin-request-detail-project')).toBeVisible()
  })

  test.skip('shows back button to return to pending requests', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Back button should be visible
    const backButton = page.getByRole('button', { name: /back to pending requests/i })
    await expect(backButton).toBeVisible()

    // Click back should return to queue
    await backButton.click()
    await expect(page).toHaveURL('/admin/requests')
  })
})

test.describe('Admin Request Detail - Requester Information (AC 2) @requires-auth @requires-backend', () => {
  test.skip('displays requester name, email, and role', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Requester name should be visible
    await expect(page.getByTestId('admin-request-detail-requester-name')).toBeVisible()

    // Requester email should be visible and be a link
    const emailLink = page.getByTestId('admin-request-detail-requester-email')
    await expect(emailLink).toBeVisible()
    await expect(emailLink).toHaveAttribute('href', /^mailto:/)

    // Requester role should be visible
    await expect(page.getByTestId('admin-request-detail-requester-role')).toBeVisible()
  })
})

test.describe('Admin Request Detail - Project Context (AC 4) @requires-auth @requires-backend', () => {
  test.skip('displays quota placeholder for Epic 4', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Quota placeholder should be visible with Epic 4 reference
    const quotaPlaceholder = page.getByTestId('admin-request-detail-quota-placeholder')
    await expect(quotaPlaceholder).toBeVisible()
    await expect(quotaPlaceholder).toContainText('Quota information available in Epic 4')
  })
})

test.describe('Admin Request Detail - Request Details (AC 3) @requires-auth @requires-backend', () => {
  test.skip('displays VM size specifications', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // CPU cores should be visible
    const cpuElement = page.getByTestId('admin-request-detail-cpu')
    await expect(cpuElement).toBeVisible()
    await expect(cpuElement).toContainText(/\d+ cores/)

    // Memory should be visible
    const memoryElement = page.getByTestId('admin-request-detail-memory')
    await expect(memoryElement).toBeVisible()
    await expect(memoryElement).toContainText(/\d+ GB/)

    // Disk should be visible
    const diskElement = page.getByTestId('admin-request-detail-disk')
    await expect(diskElement).toBeVisible()
    await expect(diskElement).toContainText(/\d+ GB/)
  })

  test.skip('displays business justification', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Justification should be visible
    await expect(page.getByTestId('admin-request-detail-justification')).toBeVisible()
  })

  test.skip('displays creation timestamp', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Created timestamp should be visible
    await expect(page.getByTestId('admin-request-detail-created')).toBeVisible()
  })
})

test.describe('Admin Request Detail - Timeline (AC 5) @requires-auth @requires-backend', () => {
  test.skip('displays request timeline with events', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Timeline should be visible
    await expect(page.getByTestId('request-timeline')).toBeVisible()

    // At minimum, should have CREATED event
    await expect(page.getByTestId('timeline-event-created')).toBeVisible()
  })

  test.skip('timeline events show timestamp and actor', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Event label should be visible
    await expect(page.getByTestId('timeline-event-label').first()).toBeVisible()

    // Event time should be visible
    await expect(page.getByTestId('timeline-event-time').first()).toBeVisible()
  })
})

test.describe('Admin Request Detail - Requester History (AC 6) @requires-auth @requires-backend', () => {
  test.skip('displays "Recent Requests" section', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // History section should be visible
    await expect(page.getByRole('heading', { name: /recent requests/i })).toBeVisible()
  })

  test.skip('shows up to 5 previous requests from same requester', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // History container should be visible
    const historyContainer = page.getByTestId('admin-request-detail-history')

    // If history exists, check it has items
    if (await historyContainer.isVisible()) {
      const historyItems = historyContainer.locator('[data-testid^="history-item-"]')
      const count = await historyItems.count()
      expect(count).toBeLessThanOrEqual(5)
    }
  })

  test.skip('history items link to their respective detail pages', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    const historyContainer = page.getByTestId('admin-request-detail-history')

    if (await historyContainer.isVisible()) {
      const firstHistoryItem = historyContainer.locator('[data-testid^="history-item-"]').first()

      if (await firstHistoryItem.isVisible()) {
        // Get the href to verify it's a link
        const href = await firstHistoryItem.getAttribute('href')
        expect(href).toMatch(/\/admin\/requests\/[\w-]+/)
      }
    }
  })

  test.skip('shows empty message when no previous requests', async ({ page }) => {
    // This test requires a request from a user with no history
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // If no history, should show empty message (conditional based on test data)
    await expect(page.getByText(/no previous requests from this user/i)).toBeVisible()
  })
})

test.describe('Admin Request Detail - Action Buttons @requires-auth @requires-backend', () => {
  test.skip('shows enabled approve/reject buttons for PENDING requests', async ({ page }) => {
    // Story 2.11: Approve/Reject Actions
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Approve button should be visible and enabled for PENDING status
    const approveButton = page.getByTestId('approve-button')
    await expect(approveButton).toBeVisible()
    await expect(approveButton).toBeEnabled()

    // Reject button should be visible and enabled for PENDING status
    const rejectButton = page.getByTestId('reject-button')
    await expect(rejectButton).toBeVisible()
    await expect(rejectButton).toBeEnabled()
  })

  test.skip('hides action buttons for non-PENDING requests', async () => {
    // Story 2.11: AC - Buttons only shown for PENDING status
    // This test requires an APPROVED request in the database
    // Navigate to a known approved request or filter by status

    // For now, this serves as documentation of expected behavior
    // When viewing an APPROVED/REJECTED request:
    // - No approve/reject buttons should be visible
  })
})

/**
 * Story 2.11: Approve Action Tests
 *
 * Tests for the approve workflow:
 * - AC 1: One-click approval via button
 * - AC 4: Success updates timeline to "APPROVED"
 * - AC 6: Optimistic locking prevents stale updates
 * - AC 7: Toast notification on success
 */
test.describe('Admin Request Detail - Approve Action @requires-auth @requires-backend', () => {
  test.skip('clicking approve button opens confirmation modal', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Click approve button
    await page.getByTestId('approve-button').click()

    // Confirmation modal should appear
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByRole('heading', { name: /approve request/i })).toBeVisible()
    await expect(page.getByText(/are you sure you want to approve/i)).toBeVisible()
  })

  test.skip('approve modal has confirm and cancel buttons', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()
    await page.getByTestId('approve-button').click()

    // Modal actions
    await expect(page.getByRole('button', { name: /cancel/i })).toBeVisible()
    await expect(page.getByTestId('approve-confirm-button')).toBeVisible()
  })

  test.skip('cancel button closes approve modal without action', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()
    await page.getByTestId('approve-button').click()

    // Click cancel
    await page.getByRole('button', { name: /cancel/i }).click()

    // Modal should close
    await expect(page.getByRole('dialog')).not.toBeVisible()

    // Request should still be PENDING
    await expect(page.locator('[data-testid="status-badge-pending"]')).toBeVisible()
  })

  test.skip('confirming approve updates status and shows success toast', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    await page.getByTestId('approve-button').click()
    await page.getByTestId('approve-confirm-button').click()

    // Modal should close
    await expect(page.getByRole('dialog')).not.toBeVisible()

    // Success toast should appear (AC 7)
    await expect(page.locator('[data-testid="toast"]')).toContainText(/approved/i)

    // Status should update to APPROVED (AC 4)
    await expect(page.locator('[data-testid="status-badge-approved"]')).toBeVisible()

    // Action buttons should be hidden for approved request
    await expect(page.getByTestId('approve-button')).not.toBeVisible()
    await expect(page.getByTestId('reject-button')).not.toBeVisible()
  })

  test.skip('approve adds APPROVED event to timeline', async ({ page }) => {
    // This test assumes the approve action was just completed
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    await page.getByTestId('approve-button').click()
    await page.getByTestId('approve-confirm-button').click()

    // Wait for success
    await expect(page.locator('[data-testid="status-badge-approved"]')).toBeVisible()

    // Timeline should now include APPROVED event (AC 4)
    await expect(page.getByTestId('timeline-event-approved')).toBeVisible()
  })
})

/**
 * Story 2.11: Reject Action Tests
 *
 * Tests for the reject workflow:
 * - AC 2: Reject with mandatory reason (10-500 chars)
 * - AC 3: Reason displayed in timeline
 * - AC 4: Success updates timeline to "REJECTED"
 * - AC 7: Toast notification on success
 */
test.describe('Admin Request Detail - Reject Action @requires-auth @requires-backend', () => {
  test.skip('clicking reject button opens rejection modal', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Click reject button
    await page.getByTestId('reject-button').click()

    // Rejection modal should appear with reason input
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByRole('heading', { name: /reject request/i })).toBeVisible()
    await expect(page.getByTestId('reject-reason-input')).toBeVisible()
  })

  test.skip('reject modal requires reason with character count', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()
    await page.getByTestId('reject-button').click()

    // Character count should be visible
    await expect(page.getByTestId('character-count')).toBeVisible()

    // Confirm button should be disabled without reason
    await expect(page.getByTestId('confirm-reject-button')).toBeDisabled()
  })

  test.skip('reject modal validates minimum reason length (10 chars)', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()
    await page.getByTestId('reject-button').click()

    // Enter too short reason
    await page.getByTestId('reject-reason-input').fill('Too short')

    // Confirm should still be disabled (less than 10 chars)
    await expect(page.getByTestId('confirm-reject-button')).toBeDisabled()

    // Enter valid reason (10+ chars)
    await page.getByTestId('reject-reason-input').fill('This is a valid rejection reason')

    // Now confirm should be enabled
    await expect(page.getByTestId('confirm-reject-button')).toBeEnabled()
  })

  test.skip('cancel button closes reject modal without action', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()
    await page.getByTestId('reject-button').click()

    await page.getByTestId('reject-reason-input').fill('This is a valid rejection reason')
    await page.getByRole('button', { name: /cancel/i }).click()

    // Modal should close
    await expect(page.getByRole('dialog')).not.toBeVisible()

    // Request should still be PENDING
    await expect(page.locator('[data-testid="status-badge-pending"]')).toBeVisible()
  })

  test.skip('confirming reject updates status and shows success toast', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    await page.getByTestId('reject-button').click()
    await page.getByTestId('reject-reason-input').fill('Resources not available for this request at this time')
    await page.getByTestId('confirm-reject-button').click()

    // Modal should close
    await expect(page.getByRole('dialog')).not.toBeVisible()

    // Success toast should appear (AC 7)
    await expect(page.locator('[data-testid="toast"]')).toContainText(/rejected/i)

    // Status should update to REJECTED (AC 4)
    await expect(page.locator('[data-testid="status-badge-rejected"]')).toBeVisible()

    // Action buttons should be hidden for rejected request
    await expect(page.getByTestId('approve-button')).not.toBeVisible()
    await expect(page.getByTestId('reject-button')).not.toBeVisible()
  })

  test.skip('reject adds REJECTED event to timeline with reason', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    const rejectionReason = 'Budget constraints prevent approval at this time'
    await page.getByTestId('reject-button').click()
    await page.getByTestId('reject-reason-input').fill(rejectionReason)
    await page.getByTestId('confirm-reject-button').click()

    // Wait for success
    await expect(page.locator('[data-testid="status-badge-rejected"]')).toBeVisible()

    // Timeline should include REJECTED event with reason (AC 3)
    await expect(page.getByTestId('timeline-event-rejected')).toBeVisible()
    await expect(page.getByTestId('timeline-event-rejected')).toContainText(rejectionReason)
  })
})

/**
 * Story 2.11: Optimistic Locking Tests
 *
 * Tests for concurrent modification handling:
 * - AC 6: Stale version error shows conflict message
 */
test.describe('Admin Request Detail - Concurrency Handling @requires-auth @requires-backend', () => {
  test.skip('shows conflict error when request was modified by another admin', async ({ page }) => {
    // This test requires simulating a concurrent modification
    // Intercept the approve/reject API call and return 409 Conflict

    await page.route('**/api/admin/requests/*/approve', async (route) => {
      await route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({
          type: 'concurrency_conflict',
          message: 'Request has been modified by another user',
        }),
      })
    })

    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    await page.getByTestId('approve-button').click()
    await page.getByTestId('approve-confirm-button').click()

    // Should show error toast with refresh prompt
    await expect(page.locator('[data-testid="toast"]')).toContainText(/modified/i)
  })
})

test.describe('Admin Request Detail - Error Handling @requires-auth', () => {
  test.skip('shows 404 page for non-existent request', async ({ page }) => {
    // Navigate directly to a non-existent request
    await page.goto('/admin/requests/non-existent-uuid-12345')

    // Should show not found state
    await expect(page.getByTestId('admin-request-detail-not-found')).toBeVisible()
    await expect(page.getByRole('heading', { name: /request not found/i })).toBeVisible()
  })

  test.skip('shows link to return to pending requests on 404', async ({ page }) => {
    await page.goto('/admin/requests/non-existent-uuid-12345')

    const returnLink = page.getByRole('link', { name: /view pending requests/i })
    await expect(returnLink).toBeVisible()

    await returnLink.click()
    await expect(page).toHaveURL('/admin/requests')
  })
})

test.describe('Admin Request Detail - Loading States @requires-auth @requires-backend', () => {
  test.skip('shows loading state while fetching data', async ({ page }) => {
    // Intercept API to slow down response
    await page.route('**/api/admin/requests/*', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 500))
      await route.continue()
    })

    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()
    await page.locator('[data-testid^="pending-request-row-"]').first().click()

    // Should see loading indicator
    await expect(page.getByTestId('admin-request-detail-loading')).toBeVisible()
  })
})

/**
 * Unauthenticated tests - can run without auth.
 */
test.describe('Admin Request Detail - Unauthenticated', () => {
  test('redirects to login when accessing /admin/requests/:id without auth', async ({ page }) => {
    await page.goto('/admin/requests/test-uuid-123')

    // Should see login button (unauthenticated state)
    await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible({
      timeout: 10000,
    })
  })
})

/**
 * Navigation tests from queue to detail.
 */
test.describe('Admin Request Detail - Queue Navigation @requires-auth @requires-backend', () => {
  test.skip('navigates from pending requests table row click', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()

    // Get the ID from the first row
    const firstRow = page.locator('[data-testid^="pending-request-row-"]').first()
    const rowId = await firstRow.getAttribute('data-testid')
    const requestId = rowId?.replace('pending-request-row-', '')

    // Click the row
    await firstRow.click()

    // URL should include the request ID
    await expect(page).toHaveURL(`/admin/requests/${requestId}`)

    // Detail page should be visible
    await expect(page.getByTestId('admin-request-detail-page')).toBeVisible()
  })

  test.skip('navigates via keyboard Enter on focused row', async ({ page }) => {
    await page.goto('/admin/requests')
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()

    // Focus and press Enter on first row
    const firstRow = page.locator('[data-testid^="pending-request-row-"]').first()
    await firstRow.focus()
    await page.keyboard.press('Enter')

    // Should navigate to detail page
    await expect(page).toHaveURL(/\/admin\/requests\/[\w-]+/)
    await expect(page.getByTestId('admin-request-detail-page')).toBeVisible()
  })
})
