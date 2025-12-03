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

/**
 * Formats an ISO date string to a human-readable relative time.
 *
 * Story 2.10: Used in requester history to show when previous requests were made.
 *
 * @param isoDate - ISO 8601 date string (e.g., "2025-01-15T14:30:00Z")
 * @returns Relative time string (e.g., "2 days ago", "1 week ago")
 */
export function formatRelativeTime(isoDate: string): string {
  const date = new Date(isoDate)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSecs = Math.floor(diffMs / 1000)
  const diffMins = Math.floor(diffSecs / 60)
  const diffHours = Math.floor(diffMins / 60)
  const diffDays = Math.floor(diffHours / 24)
  const diffWeeks = Math.floor(diffDays / 7)
  const diffMonths = Math.floor(diffDays / 30)

  if (diffSecs < 60) {
    return 'just now'
  } else if (diffMins < 60) {
    return diffMins === 1 ? '1 minute ago' : `${diffMins} minutes ago`
  } else if (diffHours < 24) {
    return diffHours === 1 ? '1 hour ago' : `${diffHours} hours ago`
  } else if (diffDays < 7) {
    return diffDays === 1 ? '1 day ago' : `${diffDays} days ago`
  } else if (diffWeeks < 4) {
    return diffWeeks === 1 ? '1 week ago' : `${diffWeeks} weeks ago`
  } else if (diffMonths < 12) {
    return diffMonths === 1 ? '1 month ago' : `${diffMonths} months ago`
  } else {
    // For older dates, show the actual date
    return formatDateTime(isoDate)
  }
}
