import type { LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export interface EmptyStateProps {
  /** Lucide icon component to display */
  icon?: LucideIcon
  /** Main title text */
  title: string
  /** Optional description text below title */
  description?: string
  /** CTA button label */
  ctaLabel?: string
  /** CTA button click handler */
  onCtaClick?: () => void
  /** Additional CSS classes */
  className?: string
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  ctaLabel,
  onCtaClick,
  className,
}: EmptyStateProps) {
  return (
    <Card className={cn('w-full', className)}>
      <CardContent className="flex flex-col items-center justify-center py-12 text-center">
        {Icon && (
          <div className="p-4 rounded-full bg-muted mb-4">
            <Icon className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
          </div>
        )}
        <h3 className="text-lg font-semibold mb-2">{title}</h3>
        {description && (
          <p className="text-sm text-muted-foreground mb-4 max-w-sm">
            {description}
          </p>
        )}
        {ctaLabel && onCtaClick && (
          <Button onClick={onCtaClick} className="mt-2">
            {ctaLabel}
          </Button>
        )}
      </CardContent>
    </Card>
  )
}
