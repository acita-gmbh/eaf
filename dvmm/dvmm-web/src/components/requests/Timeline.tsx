import {
  CheckCircle,
  XCircle,
  Clock,
  Loader2,
  Server,
  FileText,
  type LucideIcon,
} from 'lucide-react'
import type { TimelineEvent, TimelineEventType } from '@/api/vm-requests'

interface TimelineProps {
  /** List of timeline events to display (should be sorted chronologically, oldest first) */
  events: TimelineEvent[]
}

/**
 * Configuration for timeline event icons and colors.
 * Per Story 2.8 Dev Notes:
 * - CREATED: document icon, blue
 * - APPROVED: check circle, green
 * - REJECTED: x circle, red
 * - CANCELLED: x circle, orange
 * - PROVISIONING_STARTED: loader, blue
 * - PROVISIONING_QUEUED: clock, yellow
 * - VM_READY: server, green
 */
const eventConfig: Record<
  TimelineEventType,
  { icon: LucideIcon; colorClass: string; label: string }
> = {
  CREATED: {
    icon: FileText,
    colorClass: 'text-blue-500 bg-blue-100 dark:bg-blue-950',
    label: 'Request Created',
  },
  APPROVED: {
    icon: CheckCircle,
    colorClass: 'text-green-500 bg-green-100 dark:bg-green-950',
    label: 'Approved',
  },
  REJECTED: {
    icon: XCircle,
    colorClass: 'text-red-500 bg-red-100 dark:bg-red-950',
    label: 'Rejected',
  },
  CANCELLED: {
    icon: XCircle,
    colorClass: 'text-orange-500 bg-orange-100 dark:bg-orange-950',
    label: 'Cancelled',
  },
  PROVISIONING_STARTED: {
    icon: Loader2,
    colorClass: 'text-blue-500 bg-blue-100 dark:bg-blue-950',
    label: 'Provisioning Started',
  },
  PROVISIONING_QUEUED: {
    icon: Clock,
    colorClass: 'text-yellow-500 bg-yellow-100 dark:bg-yellow-950',
    label: 'Queued for Provisioning',
  },
  VM_READY: {
    icon: Server,
    colorClass: 'text-green-500 bg-green-100 dark:bg-green-950',
    label: 'VM Ready',
  },
}

/**
 * Formats an ISO date string to localized format with time.
 */
function formatDateTime(isoDate: string): string {
  return new Date(isoDate).toLocaleDateString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Parses event details JSON to extract rejection/cancellation reason.
 */
function parseEventReason(details: string | null): string | null {
  if (!details) return null
  try {
    const parsed = JSON.parse(details)
    return parsed.reason || null
  } catch {
    return null
  }
}

/**
 * Timeline component displaying chronological history of request events.
 *
 * Shows a vertical timeline with:
 * - Event type icons with appropriate colors
 * - Event labels and timestamps
 * - Actor name (who performed the action)
 * - Details like rejection/cancellation reasons
 *
 * Per AC-3: Timeline shows chronological history with dates and actors.
 */
export function Timeline({ events }: TimelineProps) {
  if (events.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground">
        No timeline events available.
      </div>
    )
  }

  return (
    <div className="space-y-1" data-testid="request-timeline">
      {events.map((event, index) => {
        const config = eventConfig[event.eventType]
        const Icon = config.icon
        const isLast = index === events.length - 1
        const reason = parseEventReason(event.details)

        return (
          <div
            key={`${event.eventType}-${event.occurredAt}-${index}`}
            className="relative flex gap-4"
            data-testid={`timeline-event-${event.eventType.toLowerCase()}`}
          >
            {/* Vertical line connecting events */}
            {!isLast && (
              <div
                className="absolute left-5 top-10 -bottom-1 w-0.5 bg-border"
                aria-hidden="true"
              />
            )}

            {/* Event icon */}
            <div
              className={`flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center ${config.colorClass}`}
            >
              <Icon className="w-5 h-5" />
            </div>

            {/* Event content */}
            <div className="flex-1 pb-6">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1">
                <p className="font-medium" data-testid="timeline-event-label">
                  {config.label}
                </p>
                <time
                  className="text-sm text-muted-foreground"
                  dateTime={event.occurredAt}
                  data-testid="timeline-event-time"
                >
                  {formatDateTime(event.occurredAt)}
                </time>
              </div>

              {event.actorName && (
                <p
                  className="text-sm text-muted-foreground mt-1"
                  data-testid="timeline-event-actor"
                >
                  by {event.actorName}
                </p>
              )}

              {reason && (
                <p
                  className="text-sm text-muted-foreground mt-2 italic"
                  data-testid="timeline-event-reason"
                >
                  Reason: {reason}
                </p>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}
