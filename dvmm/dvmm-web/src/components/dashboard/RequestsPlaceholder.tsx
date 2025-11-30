import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { FileText } from 'lucide-react'
import { EmptyState } from '@/components/empty-states'

interface RequestsPlaceholderProps {
  /** Callback when user clicks the CTA to request a VM */
  onRequestVm?: () => void
}

export function RequestsPlaceholder({ onRequestVm }: RequestsPlaceholderProps) {
  const handleCtaClick = () => {
    if (onRequestVm) {
      onRequestVm()
    } else if (import.meta.env.DEV) {
      // Placeholder action until routing is implemented in Story 2.4
      console.log('[RequestsPlaceholder] Navigate to VM request form')
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <FileText className="h-5 w-5" />
          My Requests
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <EmptyState
          icon={FileText}
          title="No VMs requested yet"
          description="Request your first virtual machine"
          ctaLabel="Request First VM"
          onCtaClick={handleCtaClick}
          className="border-0 shadow-none"
        />
      </CardContent>
    </Card>
  )
}
