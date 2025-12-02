/**
 * Shared date formatting utilities.
 */

/**
 * Formats an ISO date string to localized German format with time.
 *
 * @param isoDate - ISO 8601 date string (e.g., "2025-01-15T14:30:00Z")
 * @returns Formatted date string (e.g., "15.01.2025, 14:30")
 */
export function formatDateTime(isoDate: string): string {
  return new Date(isoDate).toLocaleDateString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
