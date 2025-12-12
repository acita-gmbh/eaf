import type { LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

// Base props without CTA
interface EmptyStateBaseProps {
  /** Lucide icon component to display */
  icon?: LucideIcon
  /** Main title text */
  title: string
  /** Optional description text below title */
  description?: string
  /** Additional CSS classes */
  className?: string
}

// Props with CTA - both label and handler required together
export interface EmptyStateWithCta extends EmptyStateBaseProps {
  /** CTA button label - required when providing onCtaClick */
  ctaLabel: string
  /** CTA button click handler - required when providing ctaLabel */
  onCtaClick: () => void
}

// Props without CTA - neither allowed
interface EmptyStateWithoutCta extends EmptyStateBaseProps {
  ctaLabel?: never
  onCtaClick?: never
}

// Union type ensures CTA props are either both provided or both omitted
export type EmptyStateProps = EmptyStateWithCta | EmptyStateWithoutCta

export function EmptyState(props: Readonly<EmptyStateProps>) {
  const { icon: Icon, title, description, className } = props

  // Type guard - if ctaLabel exists, onCtaClick is guaranteed by the union type
  const hasCta = 'ctaLabel' in props && props.ctaLabel !== undefined
  // Extract CTA props once to avoid repeated type assertions
  const ctaLabel = hasCta ? (props as EmptyStateWithCta).ctaLabel : undefined
  const onCtaClick = hasCta ? (props as EmptyStateWithCta).onCtaClick : undefined

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
        {hasCta && (
          <Button onClick={onCtaClick} className="mt-2">
            {ctaLabel}
          </Button>
        )}
      </CardContent>
    </Card>
  )
}
