import { CheckCircle2 } from 'lucide-react'
import { EmptyState } from './EmptyState'
import { cn } from '@/lib/utils'

interface AdminQueueEmptyStateProps {
  className?: string
}

/**
 * Empty state for the admin approval queue when all requests have been processed.
 * Displays a positive message to indicate all work is complete.
 */
export function AdminQueueEmptyState({ className }: AdminQueueEmptyStateProps) {
  return (
    <EmptyState
      icon={CheckCircle2}
      title="No pending approvals"
      description="All requests have been processed"
      className={cn('[&_svg]:text-emerald-500', className)}
    />
  )
}
