import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

interface StatsCardProps {
  title: string
  value: number
  icon: React.ReactNode
  variant: 'pending' | 'approved' | 'info'
}

const variantStyles = {
  pending: {
    text: 'text-[hsl(var(--status-pending))]',
    bg: 'bg-[hsl(var(--status-pending)/0.1)]',
  },
  approved: {
    text: 'text-[hsl(var(--status-approved))]',
    bg: 'bg-[hsl(var(--status-approved)/0.1)]',
  },
  info: {
    text: 'text-[hsl(var(--status-info))]',
    bg: 'bg-[hsl(var(--status-info)/0.1)]',
  },
}

export function StatsCard({ title, value, icon, variant }: StatsCardProps) {
  const styles = variantStyles[variant]

  return (
    <Card>
      <CardContent className="p-6">
        <div className="flex items-center gap-4">
          <div className={cn('p-3 rounded-lg', styles.bg)}>
            <div className={styles.text}>{icon}</div>
          </div>
          <div>
            <p className="text-sm font-medium text-muted-foreground">{title}</p>
            <p className={cn('text-2xl font-bold', styles.text)}>{value}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
