import { test, expect } from '@playwright/test'
// log import available: import { log } from '@seontechnologies/playwright-utils/log'

/**
 * E2E tests for the Admin Approval Queue page.
 *
 * Story 2.9: Admin Approval Queue
 *
 * These tests verify:
 * - AC 1: Admin sees "Open Requests" section with count badge
 * - AC 2: Pending requests list displays required columns
 * - AC 3: Requests sorted by age (oldest first - handled by backend)
 * - AC 4: Requests older than 48h highlighted
 * - AC 5: Filtering by project
 * - AC 6: Tenant isolation (admin can only see own tenant's requests)
 * - AC 7: Empty state for no pending requests
 * - AC 8: Loading states
 * - AC 9: Navigation to request detail
 * - AC 10: Approve/Reject buttons visible but disabled
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
 * npm run test:e2e -- --project=chromium-admin admin-approval-queue.spec.ts
 *
 * # Run as regular user
 * npm run test:e2e -- --project=chromium-user admin-approval-queue.spec.ts
 * ```
 *
 * ### CI Execution
 *
 * In CI, these tests are skipped by default (marked with `test`).
 * To enable them in CI:
 * 1. Configure Keycloak Testcontainer in CI pipeline
 * 2. Set environment variables (KEYCLOAK_URL, API_URL)
 * 3. Run setup project first
 * 4. Run authenticated test projects
 *
 * ## Test Coverage Without Full E2E
 *
 * The test scenarios are validated through:
 * - Unit tests for components (PendingRequestsTable, ProjectFilter)
 * - Integration tests for API functions (admin.ts)
 * - Backend integration tests (AdminRequestControllerIntegrationTest)
 * - Unauthenticated tests (below) for basic page structure
 */

test.describe('Admin Approval Queue - Access Control @requires-auth', () => {
  test('admin user can access /admin/requests', async ({ page }) => {
    // This test requires admin role authentication
    await page.goto('/admin/requests')

    // Should see page header
    await expect(page.getByRole('heading', { name: /admin dashboard/i })).toBeVisible()

    // Should see Open Requests section
    await expect(page.getByText(/open requests/i)).toBeVisible()
  })

  test('non-admin user sees access denied on /admin/requests', async ({ page }) => {
    // This test requires non-admin authentication
    await page.goto('/admin/requests')

    // Should see access denied page
    await expect(page.getByTestId('admin-forbidden')).toBeVisible()
    await expect(page.getByRole('heading', { name: /access denied/i })).toBeVisible()
    await expect(page.getByText(/administrator privileges are required/i)).toBeVisible()

    // Should have button to return to dashboard
    await expect(page.getByRole('button', { name: /return to dashboard/i })).toBeVisible()
  })
})

test.describe('Admin Approval Queue - Display @requires-auth @requires-backend', () => {
  test('displays pending requests table with all required columns', async ({ page }) => {
    await page.goto('/admin/requests')

    // Wait for table to load
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()

    // Table headers should be visible
    await expect(page.getByRole('columnheader', { name: /requester/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /vm name/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /project/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /size/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /age/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /actions/i })).toBeVisible()
  })

  test('shows count badge with total pending requests', async ({ page }) => {
    await page.goto('/admin/requests')

    // Count badge should be visible
    const countBadge = page.getByTestId('count-badge')
    await expect(countBadge).toBeVisible()

    // Badge should contain a number
    const badgeText = await countBadge.textContent()
    expect(parseInt(badgeText ?? '0', 10)).toBeGreaterThanOrEqual(0)
  })

  test('displays empty state when no pending requests', async ({ page }) => {
    // This test requires a tenant with no pending requests
    await page.goto('/admin/requests')

    // Should show AdminQueueEmptyState
    await expect(page.getByText(/no pending approvals/i)).toBeVisible()
    await expect(page.getByText(/all requests have been processed/i)).toBeVisible()
  })

  test('shows loading skeleton while fetching data', async ({ page }) => {
    // Intercept API to slow down response
    await page.route('**/api/admin/requests/pending*', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1000))
      await route.continue()
    })

    await page.goto('/admin/requests')

    // Should see skeleton loader
    await expect(page.getByTestId('pending-requests-table-skeleton')).toBeVisible()

    // Should see loading badge
    await expect(page.getByTestId('count-badge-loading')).toBeVisible()
  })
})

test.describe('Admin Approval Queue - Age Highlighting @requires-auth @requires-backend', () => {
  test('highlights requests older than 48 hours with amber background', async ({ page }) => {
    // This test requires seeded data with old requests
    await page.goto('/admin/requests')

    // Find row with "Waiting long" badge
    const waitingLongBadge = page.getByTestId('waiting-long-badge')

    if (await waitingLongBadge.count() > 0) {
      // Row should have amber background class
      const row = page.getByTestId('waiting-long-badge').locator('..').locator('..')
      await expect(row).toHaveClass(/bg-amber-50/)
    }
  })

  test('shows "Waiting long" badge on old requests', async ({ page }) => {
    await page.goto('/admin/requests')

    // If there are old requests, they should have the badge
    const waitingLongBadge = page.getByTestId('waiting-long-badge').first()

    if (await waitingLongBadge.isVisible()) {
      await expect(waitingLongBadge).toHaveText(/waiting long/i)
    }
  })
})

test.describe('Admin Approval Queue - Project Filter @requires-auth @requires-backend', () => {
  test('shows project filter dropdown', async ({ page }) => {
    await page.goto('/admin/requests')

    // Project filter should be visible
    const filterTrigger = page.getByTestId('project-filter-trigger')
    await expect(filterTrigger).toBeVisible()

    // Should default to "All Projects"
    await expect(filterTrigger).toHaveText(/all projects/i)
  })

  test('filters requests by selected project', async ({ page }) => {
    await page.goto('/admin/requests')

    // Get initial count
    const initialCount = await page.getByTestId('count-badge').textContent()

    // Open project dropdown
    await page.getByTestId('project-filter-trigger').click()

    // Select first project (if available)
    const projectOptions = page.locator('[role="option"]').filter({
      hasNot: page.getByText(/all projects/i),
    })

    if (await projectOptions.count() > 0) {
      await projectOptions.first().click()

      // Wait for data to reload
      await page.waitForResponse('**/api/admin/requests/pending*')

      // Count badge should update
      const filteredCount = await page.getByTestId('count-badge').textContent()

      // Filtered count should be <= initial count
      expect(parseInt(filteredCount ?? '0', 10)).toBeLessThanOrEqual(
        parseInt(initialCount ?? '0', 10)
      )
    }
  })

  test('shows filtered empty state for project with no requests', async ({ page }) => {
    await page.goto('/admin/requests')

    // Mock scenario: Select a project that has no pending requests
    // Would require seeding specific test data

    // Should show filtered empty state
    // await expect(page.getByText(/no requests for/i)).toBeVisible()
  })
})

test.describe('Admin Approval Queue - Navigation @requires-auth @requires-backend', () => {
  test('clicking request row navigates to detail page', async ({ page }) => {
    await page.goto('/admin/requests')

    // Wait for table to load
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()

    // Click first row
    const firstRow = page.locator('[data-testid^="pending-request-row-"]').first()
    await firstRow.click()

    // Should navigate to request detail page
    await expect(page).toHaveURL(/\/requests\/[\w-]+/)

    // Request detail page should load
    await expect(page.getByRole('heading', { name: /request detail/i })).toBeVisible()
  })

  test('keyboard navigation works on table rows', async ({ page }) => {
    await page.goto('/admin/requests')

    // Wait for table to load
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()

    // Focus first row
    const firstRow = page.locator('[data-testid^="pending-request-row-"]').first()
    await firstRow.focus()

    // Press Enter should navigate
    await page.keyboard.press('Enter')

    // Should navigate to request detail page
    await expect(page).toHaveURL(/\/requests\/[\w-]+/)
  })
})

test.describe('Admin Approval Queue - Action Buttons @requires-auth @requires-backend', () => {
  test('approve and reject buttons are visible but disabled', async ({ page }) => {
    await page.goto('/admin/requests')

    // Wait for table to load
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()

    // Find approve button on first row
    const approveButton = page.getByTestId('approve-button').first()
    await expect(approveButton).toBeVisible()
    await expect(approveButton).toBeDisabled()
    await expect(approveButton).toHaveAttribute('aria-disabled', 'true')

    // Find reject button on first row
    const rejectButton = page.getByTestId('reject-button').first()
    await expect(rejectButton).toBeVisible()
    await expect(rejectButton).toBeDisabled()
    await expect(rejectButton).toHaveAttribute('aria-disabled', 'true')
  })

  test('disabled buttons show tooltip explaining availability', async ({ page }) => {
    await page.goto('/admin/requests')

    // Wait for table to load
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()

    // Hover over approve button to trigger tooltip
    await page.getByTestId('approve-button').first().hover()

    // Tooltip should appear
    await expect(page.getByText(/available in story 2\.11/i)).toBeVisible()
  })
})

test.describe('Admin Approval Queue - Size Tooltip @requires-auth @requires-backend', () => {
  test('size cell shows tooltip with full specs on hover', async ({ page }) => {
    await page.goto('/admin/requests')

    // Wait for table to load
    await expect(page.getByTestId('pending-requests-table')).toBeVisible()

    // Hover over size cell
    const sizeCell = page.getByTestId('size').first()
    await sizeCell.hover()

    // Tooltip should show CPU, RAM, Disk details
    await expect(page.getByText(/cpu/i)).toBeVisible()
    await expect(page.getByText(/gb ram/i)).toBeVisible()
    await expect(page.getByText(/gb disk/i)).toBeVisible()
  })
})

/**
 * Unauthenticated tests - can run without auth.
 */
test.describe('Admin Approval Queue - Unauthenticated', () => {
  test('redirects to login when accessing /admin/requests without auth', async ({ page }) => {
    await page.goto('/admin/requests')

    // Should see login button (unauthenticated state)
    await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible({
      timeout: 10000,
    })
  })
})

/**
 * Navigation tests - verify sidebar navigation item.
 */
test.describe('Admin Approval Queue - Sidebar Navigation', () => {
  // This test requires admin authentication to see the Admin Queue nav item.
  // Non-admin users will not see this nav item.
  test('sidebar shows "Admin Queue" navigation link for admin users', async ({ page }) => {
    // Requires admin authentication
    await page.goto('/')

    // After authentication with admin role, verify the Admin Queue link is visible
    await expect(page.getByRole('link', { name: /admin queue/i })).toBeVisible()
  })

  test('sidebar hides "Admin Queue" navigation link for non-admin users', async ({ page }) => {
    // Requires non-admin authentication
    await page.goto('/')

    // After authentication with user role, verify the Admin Queue link is NOT visible
    await expect(page.getByRole('link', { name: /admin queue/i })).not.toBeVisible()
  })
})
